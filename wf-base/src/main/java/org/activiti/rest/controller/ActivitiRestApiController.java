package org.activiti.rest.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import liquibase.util.csv.CSVWriter;

import org.activiti.engine.*;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Attachment;
import org.activiti.engine.task.DelegationState;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.activiti.redis.exception.RedisException;
import org.activiti.redis.service.RedisService;
import org.activiti.rest.controller.adapter.AttachmentEntityAdapter;
import org.activiti.rest.controller.adapter.ProcDefinitionAdapter;
import org.activiti.rest.controller.adapter.TaskAssigneeAdapter;
import org.activiti.rest.controller.entity.*;
import org.activiti.rest.controller.entity.Process;
import org.activiti.rest.service.api.runtime.process.ExecutionBaseResource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.wf.dp.dniprorada.base.model.AbstractModelTask;
import org.wf.dp.dniprorada.engine.task.FileTaskUpload;
import org.wf.dp.dniprorada.model.BuilderAtachModel;
import org.wf.dp.dniprorada.model.ByteArrayMultipartFileOld;

/**
 * ...wf-region/service/...
 * Example:
 * .../wf-region/service/rest/startProcessByKey/citizensRequest
 *
 * @author Tereshchenko
 */
@Controller
@RequestMapping(value = "/rest")
public class ActivitiRestApiController extends ExecutionBaseResource {

    private static final Logger log = LoggerFactory.getLogger(ActivitiRestApiController.class);

    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RedisService redisService;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private IdentityService identityService;
    @Autowired
    private FormService formService;


    @RequestMapping(value = "/start-process/{key}", method = RequestMethod.GET)
    @Transactional
    public
    @ResponseBody
    ProcessI startProcessByKey(@PathVariable("key") String key) {
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(key);
        if (pi == null || pi.getId() == null) {
            throw new IllegalArgumentException(String.format(
                    "process did not started by key:{%s}", key));
        }
        return new Process(pi.getProcessInstanceId());
    }

    @RequestMapping(value = "/tasks/{assignee}", method = RequestMethod.GET)
    @Transactional
    public
    @ResponseBody
    List<TaskAssigneeI> getTasksByAssignee(@PathVariable("assignee") String assignee) {
        List<Task> tasks = taskService.createTaskQuery().taskAssignee(assignee).list();
        List<TaskAssigneeI> facadeTasks = new ArrayList<>();
        TaskAssigneeAdapter adapter = new TaskAssigneeAdapter();
        for (Task task : tasks) {
            facadeTasks.add(adapter.apply(task));
        }
        return facadeTasks;
    }


    @RequestMapping(value = "/process-definitions", method = RequestMethod.GET)
    @Transactional
    public
    @ResponseBody
    List<ProcDefinitionI> getProcessDefinitions() {
        List<ProcessDefinition> processDefinitions = repositoryService.createProcessDefinitionQuery().latestVersion().list();
        List<ProcDefinitionI> procDefinitions = new ArrayList<>();
        ProcDefinitionAdapter adapter = new ProcDefinitionAdapter();
        for (ProcessDefinition processDefinition : processDefinitions) {
            procDefinitions.add(adapter.apply(processDefinition));
        }
        return procDefinitions;
    }


    /**
     * Укладываем в редис multipartFileToByteArray 
     * @param file
     * @return
     * @throws org.activiti.rest.controller.ActivitiIOException
     */
    @RequestMapping(value = "/file/upload_file_to_redis", method = RequestMethod.POST)
    @Transactional
    public
    @ResponseBody
    String putAttachmentsToRedis(@RequestParam("file") MultipartFile file) throws ActivitiIOException, Exception  {
    	String atachId = null;
		try {
			atachId = redisService.putAttachments(AbstractModelTask.multipartFileToByteArray(file).toByteArray());
		//}catch (RedisException e) {
		}catch (Exception e) {
			 //throw new ActivitiIOException(ActivitiIOException.Error.REDIS_ERROR,e.getMessage());
			 throw e;
		/*} catch (IOException e) {
			throw new ActivitiIOException(ActivitiIOException.Error.REDIS_ERROR,e.getMessage());*/
		}
		return atachId;
    }
    
    
    @RequestMapping(value = "/file/download_file_from_redis", method = RequestMethod.GET)
    @Transactional
    public
    @ResponseBody
    byte[] getAttachmentsFromRedis(@RequestParam("key") String key) throws ActivitiIOException  {
    	byte[] upload =null;
    	try {
    		upload =  redisService.getAttachments(key);
		} catch (RedisException e) {
			throw new ActivitiIOException(ActivitiIOException.Error.REDIS_ERROR,e.getMessage());
		}
		return upload;
    }


    /**
     * Получение Attachment средствами активити из таблицы ACT_HI_ATTACHMENT
     * @param taskId
     * @param attachmentId
     * @param nFile
     * @param request
     * @param httpResponse
     * @return
     * @throws java.io.IOException
     */
    @RequestMapping(value = "/file/download_file_from_db", method = RequestMethod.GET)
    @Transactional
    public @ResponseBody
    byte[] getAttachmentFromDb(@RequestParam(value = "taskId") String taskId,
                               @RequestParam(required = false, value = "attachmentId") String attachmentId,
                               @RequestParam(required = false, value = "nFile") Integer nFile,
    		             HttpServletRequest request, HttpServletResponse httpResponse) throws IOException {
    	
    	//Получаем по задаче ид процесса
    	HistoricTaskInstance historicTaskInstanceQuery = historyService.createHistoricTaskInstanceQuery()
        		.taskId(taskId).singleResult(); 
    	String processInstanceId = historicTaskInstanceQuery.getProcessInstanceId();
    	if (processInstanceId == null) {
            throw new ActivitiObjectNotFoundException(
                    "ProcessInstanceId for taskId '" + taskId + "' not found.",
                    Attachment.class);
        }
    	
        //Выбираем по процессу прикрепленные файлы
        List<Attachment> attachments = taskService.getProcessInstanceAttachments(processInstanceId);
        Attachment attachmentRequested = null;
        for (int i = 0; i < attachments.size(); i++) {
            if (attachments.get(i).getId().equalsIgnoreCase(attachmentId)) {
                attachmentRequested = attachments.get(i);
                break;
            }
            if (null != nFile && nFile.equals(i+1)) {
                attachmentRequested = attachments.get(i);
                break;
            }
        }

        if (attachmentRequested == null && !attachments.isEmpty()) {
            attachmentRequested = attachments.get(0);
        }

        if (attachmentRequested == null) {
            throw new ActivitiObjectNotFoundException(
                    "Attachment for taskId '" + taskId + "' not found.",
                    Attachment.class);
        }

        InputStream attachmentStream = taskService.getAttachmentContent(attachmentRequested.getId());
        if (attachmentStream == null) {
            throw new ActivitiObjectNotFoundException(
                    "Attachment for taskId '" + taskId + "' doesn't have content associated with it.",
                    Attachment.class);
        }

        //Вычитывем из потока массив байтов контента и помещаем параметры контента в header 
		ByteArrayMultipartFileOld multipartFile = new ByteArrayMultipartFileOld(
				attachmentStream, attachmentRequested.getDescription(),
				attachmentRequested.getName(), attachmentRequested.getType());

        //httpResponse.setHeader("Content-disposition", "attachment; filename=" + composeFileName(multipartFile));
        //httpResponse.setHeader("Content-Type", multipartFile.getContentType() + ";charset=UTF-8");
        httpResponse.setHeader("Content-disposition", "attachment; filename=" + attachmentRequested.getName());
        httpResponse.setHeader("Content-Type","application/octet-stream");
        
        httpResponse.setContentLength(multipartFile.getBytes().length);
      
        return multipartFile.getBytes();
    }
    
    private String composeFileName(ByteArrayMultipartFileOld multipartFile){
    	return multipartFile.getName() + (multipartFile.getExp() != null 
    				? "." + multipartFile.getExp() 
    				: "");
    }

    
    /**
     * Сервис для получения Attachment из execution
     * @param taskId
     * @param request
     * @param httpResponse
     * @return
     * @throws java.io.IOException
     */
    
    @RequestMapping(value = "/file/download_file_from_db_execution", method = RequestMethod.GET)
    @Transactional
    public @ResponseBody
    byte[] getAttachmentFromDbExecution(@RequestParam("taskId") String taskId, 
    		             HttpServletRequest request, HttpServletResponse httpResponse) throws IOException {
    	
    	//получаем по задаче ид процесса
    	HistoricTaskInstance historicTaskInstanceQuery = historyService.createHistoricTaskInstanceQuery()
        		.taskId(taskId).singleResult();
    	String processInstanceId = historicTaskInstanceQuery.getProcessInstanceId();
    	if (processInstanceId == null) {
            throw new ActivitiObjectNotFoundException(String.format(
        			"ProcessInstanceId for taskId '{%s}' not found.", taskId),
                    Attachment.class);
        }
    	
    	//получаем по ид процесса сам процесс
    	HistoricProcessInstance processInstance = historyService.createHistoricProcessInstanceQuery()
    	    	.processInstanceId(processInstanceId)
    	    	.includeProcessVariables()
    	    	.singleResult();
    	if (processInstance == null) {
    		throw new ActivitiObjectNotFoundException(String.format(
    			"ProcessInstance for processInstanceId '{%s}' not found.", processInstanceId),
                Attachment.class);
    	}
    	
    	//получаем коллекцию переменных процеса 
    	Map<String, Object> processVariables = processInstance.getProcessVariables();
    	if (processVariables == null || processVariables.get(FileTaskUpload.BUILDER_ATACH_MODEL_LIST) == null
    			|| ((List<BuilderAtachModel>)processVariables.get(FileTaskUpload.BUILDER_ATACH_MODEL_LIST)).get(0) == null) {
			throw new ActivitiObjectNotFoundException(String.format(
					"ProcessVariable '{%s}' for processInstanceId '{%s}' not found.", 
					FileTaskUpload.BUILDER_ATACH_MODEL_LIST, processInstanceId));
		} 
    	
    	//получаем прикрепленный файл
    	BuilderAtachModel atachModel = 
    			((List<BuilderAtachModel>) processVariables.get(FileTaskUpload.BUILDER_ATACH_MODEL_LIST)).get(0);

        //Помещаем параметры контента в header 
        /*httpResponse.setHeader("Content-disposition", 
        		"attachment; filename=" + atachModel.getOriginalFilename() + "." + atachModel.getExp());*/
        httpResponse.setHeader("Content-disposition",
                "attachment; filename=" + atachModel.getOriginalFilename());
        httpResponse.setHeader("Content-Type", atachModel.getContentType() + ";charset=UTF-8");
        httpResponse.setContentLength(atachModel.getByteToStringContent().getBytes().length);
        
        return AbstractModelTask.contentStringToByte(atachModel.getByteToStringContent());
    }

    /**
     * прикрепляем к таске Attachment.
     * @param file
     * @return
     * @throws org.activiti.rest.controller.ActivitiIOException
     */
    @RequestMapping(value = "/file/upload_file_as_attachment", method = RequestMethod.POST, produces = "application/json")
    @Transactional
    public
    @ResponseBody
    AttachmentEntityI putAttachmentsToExecution(@RequestParam(value = "taskId") String taskId,
                                                @RequestParam("file") MultipartFile file,
                                                @RequestParam(value = "description") String description) throws ActivitiIOException, Exception {

        String processInstanceId = null;
        String assignee = null;

        List<Task> tasks = taskService.createTaskQuery().taskId(taskId).list();
        if (tasks != null && !tasks.isEmpty()) {
            Task task = tasks.iterator().next();
            processInstanceId = task.getProcessInstanceId();
            assignee = task.getAssignee() != null ? task.getAssignee() : "kermit";
            System.out.println("processInstanceId: " + processInstanceId + " taskId: " + taskId + "assignee: " + assignee);
        } else {
            System.out.println("There is no tasks at all!");

        }

        identityService.setAuthenticatedUserId(assignee);

        System.out.println("FileExtention: " + getFileExtention(file) + " fileContentType: " + file.getContentType() + "fileName: " + file.getOriginalFilename());
        System.out.println("description: " + description);

        Attachment attachment = taskService.createAttachment(file.getContentType()
                        + ";"
                        + getFileExtention(file),
                taskId,
                processInstanceId,
                file.getOriginalFilename(),
                description, file.getInputStream());

        AttachmentEntityAdapter adapter = new AttachmentEntityAdapter();

        return adapter.apply(attachment);
    }
    
    /**
     * Получение статистики по бизнес процессу за указанные период
     * @param sID_BP_Name - ИД бизнес процесса
     * @param dateAt - дата начала периода выборки
     * @param dateTo - дата окончания периода выборки
     * @param nRowStart - позиция начальной строки для возврата (0 по умолчанию)
     * @param nRowsMax - количество записей для возврата (1000 по умолчанию)
     * @param request
     * @param httpResponse
     * @return
     * @throws java.io.IOException
     */
    @RequestMapping(value = "/file/download_bp_timing", method = RequestMethod.GET)
    @Transactional
    public void getTimingForBusinessProcess(@RequestParam(value = "sID_BP_Name") String sID_BP_Name,
    		@RequestParam(value = "sDateAt") @DateTimeFormat(pattern="yyyy-MM-dd") Date dateAt,
    		@RequestParam(value = "sDateTo", required = false) @DateTimeFormat(pattern="yyyy-MM-dd") Date dateTo,
    		@RequestParam(value = "nRowStart", required = false, defaultValue = "0") Integer nRowStart, 
    		@RequestParam(value = "nRowsMax", required = false, defaultValue = "1000") Integer nRowsMax,
    		             HttpServletRequest request, HttpServletResponse httpResponse) throws IOException {

    	if (sID_BP_Name == null || sID_BP_Name.isEmpty()) {
    		log.error("ID of business process is {}", sID_BP_Name);
            throw new ActivitiObjectNotFoundException(
                    "Statistics for the business process '" + sID_BP_Name + "' not found.",
                    Process.class);
        }

    	List<HistoricTaskInstance> foundResults = historyService.createHistoricTaskInstanceQuery()
    			.taskCompletedAfter(dateAt)
    			.taskCompletedBefore(dateTo)
    			.processDefinitionKey(sID_BP_Name)
    			.listPage(nRowStart, nRowsMax); 

    	SimpleDateFormat sdfFileName = new SimpleDateFormat("yyyy-MM-ddHH-mm-ss");
        String fileName = sID_BP_Name + "_" + sdfFileName.format(Calendar.getInstance().getTime()) + ".csv";

        log.debug("File name to return statistics : {}", fileName);
        
		httpResponse.setContentType("text/csv;charset=UTF-8");
        httpResponse.setHeader("Content-disposition", "attachment; filename=" + fileName);

    	CSVWriter csvWriter = new CSVWriter(httpResponse.getWriter());
    	
    	String[] header = { "Assignee", "Start Time", "Duration in millis", "Duration in hours", "Name of Task" };
    	csvWriter.writeNext(header);
    	
    	SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd:HH-mm-ss");
    	if (foundResults != null && foundResults.size() > 0){
	    	log.debug(String.format("Found {0} completed tasks for business process {1} for date period {2} - {3}", foundResults.size(), sID_BP_Name, sdfDate.format(dateAt), 
	    			sdfDate.format(dateTo)));
	        for (HistoricTaskInstance currTask : foundResults) {
	        	String[] line = new String[5];
	        	line[0] = currTask.getAssignee();
	            Date startDate = currTask.getStartTime();
	            line[1] = sdfDate.format(startDate);
	            line[2] = String.valueOf(currTask.getDurationInMillis());
	            long durationInHours = currTask.getDurationInMillis() / (1000 * 60 * 60);
	            line[3] = String.valueOf(durationInHours);
	            line[4] = currTask.getName();
	            
	            csvWriter.writeNext(line);
	        }
    	} else {
    		log.debug(String.format("No completed tasks found for business process {0} for date period {1} - {2}", sID_BP_Name, sdfDate.format(dateAt),
                    sdfDate.format(dateTo)));
    	}

        csvWriter.close();
    }

    /**
     * Download information about the tasks in csv format
     *
     * @param sID_BP business process name
     * @param sID_State_BP task state id
     * @param saFields field of the tasks to download. Separated by comma
     * @param nASCI_Spliter splitter of the fields
     * @param sID_Codepage encoding for the file
     * @param sDateCreateFormat format for sDateCreate
     * @param dateAt start date for the filter
     * @param dateTo end date for the filter
     * @param nRowStart start row for paging
     * @param nRowsMax maximal amount of row for paging
     * @param httpRequest http request wrapper
     * @param httpResponse http responce wrapper
     * @throws IOException in case of connection aborted with client
     *
     * example: https://test.region.igov.org.ua/wf-region/service/rest/file/downloadTasksData?sID_BP=kiev_mreo_1&sDateAt=2015-06-28&sDateTo=2015-08-01&nASCI_Spliter=59&sID_Codepage=UTF8&saFields=nID_Task;bankIdPassport;bankIdlastName;bankIdfirstName;bankIdmiddleName;1;sDateCreate
     */
    @RequestMapping(value = "/file/downloadTasksData", method = RequestMethod.GET)
    @Transactional
    public void downloadTasksData(@RequestParam(value = "sID_BP") String sID_BP,
            @RequestParam(value = "sID_State_BP", required = false) String sID_State_BP,
            @RequestParam(value = "saFields") String saFields,
            @RequestParam(value = "nASCI_Spliter") String nASCI_Spliter,
            @RequestParam(value = "sFileName", required = false) String fileName,
            @RequestParam(value = "sID_Codepage", required = false, defaultValue = "win1251") String sID_Codepage,
            @RequestParam(value = "sDateCreateFormat", required = false, defaultValue = "yyyy-MM-dd HH:mm:ss") String sDateCreateFormat,
            @RequestParam(value = "sDateAt", required = false) @DateTimeFormat(pattern="yyyy-MM-dd") Date dateAt,
            @RequestParam(value = "sDateTo", required = false) @DateTimeFormat(pattern="yyyy-MM-dd") Date dateTo,
            @RequestParam(value = "nRowStart", required = false, defaultValue = "0") Integer nRowStart,
            @RequestParam(value = "nRowsMax", required = false, defaultValue = "1000") Integer nRowsMax,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
        //1. validation
        if (StringUtils.isBlank(sID_BP)) {
            log.error("Wrong name of business task - {}", sID_BP);
            throw new ActivitiObjectNotFoundException(
                    "Statistics for the business task '" + sID_BP + "' not found. Wrong BP name.",
                    Task.class);
        }

        DelegationState delegationState = null;
        try {
            if(sID_State_BP != null) {
                delegationState = DelegationState.valueOf(sID_State_BP.toUpperCase());
            }
        } catch (IllegalArgumentException e){
            log.error("Do not support bussiness status - {}", sID_State_BP, e);
            throw new ActivitiObjectNotFoundException(
                    "Statistics for the business task with state '" + sID_State_BP + "' not found. Wrong state.",
                    Task.class, e);
        }

        if(dateAt == null){
            dateAt = DateTime.now().minusDays(1).toDate();
            log.debug("No dateAt was set, use - {}", dateAt);
        }
        if(dateTo == null){
            dateTo = DateTime.now().toDate();
            log.debug("No dateTo was set, use - {}", dateTo);
        }

        if(!NumberUtils.isNumber(nASCI_Spliter)){
            log.error("ASCI code is not a number {}", nASCI_Spliter);
            throw new ActivitiObjectNotFoundException(
                    "Statistics for the business task with name '" + sID_BP + "' not found. Wrong splitter.",
                    Task.class);
        }
        Character separator = (char) NumberUtils.toInt(nASCI_Spliter);

        Charset charset;
        try {
            if(sID_Codepage.replaceAll("-", "").equalsIgnoreCase("win1251") || sID_Codepage.replaceAll("-", "").equalsIgnoreCase("CL8MSWIN1251")){
                sID_Codepage = "CP1251";    //hack for alias
            }
            charset = Charset.forName(sID_Codepage);
            log.debug("use charset - {}", charset);
        } catch (IllegalArgumentException e) {
            log.error("Do not support charset - {}", sID_Codepage, e);
            throw new ActivitiObjectNotFoundException(
                    "Statistics for the business task for chatset '" + sID_Codepage + "' cannot be construct.",
                    Task.class, e);
        }

        TaskDownloadHeaders taskDownloadHeaders = new TaskDownloadHeaders(saFields);
//        boolean allFileds = "*".equals(saFields.trim());
//        List<String> fieldNames = Arrays.asList(saFields.toLowerCase().split(";"));
        log.info("List of fields to retrieve: " + saFields.toString());
        //2. query
        TaskQuery query = taskService.createTaskQuery()
                .processDefinitionKey(sID_BP)
                .taskCreatedAfter(dateAt)
                .taskCreatedBefore(dateTo);
        if(delegationState != null){
            query = query.taskDelegationState(delegationState);
        }
        List<Task> foundResults = query.listPage(nRowStart, nRowsMax);

        //3. response
        SimpleDateFormat sdfFileName = new SimpleDateFormat("yyyy-MM-ddHH-mm-ss");
        fileName = fileName != null? fileName
                : "data_BP-" + sID_BP + "_" + sdfFileName.format(Calendar.getInstance().getTime()) + ".txt";
        SimpleDateFormat sDateCreateDF = new SimpleDateFormat(sDateCreateFormat);

        log.debug("File name to return statistics : {}", fileName);

        httpResponse.setContentType("text/csv;charset=" + charset.name());
        httpResponse.setHeader("Content-disposition", "attachment; filename=" + fileName);

        CSVWriter csvWriter = new CSVWriter(httpResponse.getWriter(), separator);

        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd:HH-mm-ss");
        if (foundResults != null && foundResults.size() > 0){
            log.info(String.format("Found %s tasks for business process %s for date period %s - %s",
                    foundResults.size(),
                    sID_BP,
                    sdfDate.format(dateAt),
                    sdfDate.format(dateTo)));

            boolean firstStep = true;
            List<String> headers = new ArrayList<>();

            for (Task curTask : foundResults) {
                List<String> row = new ArrayList<>();
                row.add(curTask.getId());
                row.add(sDateCreateDF.format(curTask.getCreateTime()));
                if(firstStep){
                    headers.add("nID_Task");
                    headers.add("sDateCreate");
                }

                log.trace("Process task - {}", curTask);
                TaskFormData data = formService.getTaskFormData(curTask.getId());
                for(FormProperty property : data.getFormProperties()){
//                	log.trace(String.format("Matching property %s:%s:%s with fieldNames", property.getId(), property.getName(), property.getType().getName()));
//                    if(allFileds || fieldNames.contains(property.getId().toLowerCase())){
//                        if(firstStep){ //build headers from properties if all fields are requested
//                            headers.add(property.getId());
//                        }
//                        String column;
//                        if("enum".equalsIgnoreCase(property.getType().getName())){
//                            column = parseEnumProperty(property);
//                        } else {
//                            column = property.getValue();
//                        }
                    row.addAll(taskDownloadHeaders.getValues(property));
                    if(firstStep){
                        csvWriter.writeNext(taskDownloadHeaders.getHeaders(property));
                    }
//                    }
                }

                csvWriter.writeNext(row.toArray(new String[0]));
                firstStep = false;
            }
        } else {
            log.debug(String.format("No tasks found for business process %s for date period %s - %s",
                    sID_BP,
                    sdfDate.format(dateAt),
                    sdfDate.format(dateTo)));
        }

        csvWriter.close();
    }

    public static String parseEnumProperty(FormProperty property) {
        Object valuesObj = property.getType().getInformation("values");
        if(valuesObj instanceof Map) {
            Map<String, String> values = (Map) valuesObj;
            return parseEnumValue(values.get(property.getValue()));
        } else {
            log.error("Cannot parse values for property - {}", property);
            return "";
        }
    }

    public static String parseEnumValue(String enumName) {
        enumName = StringUtils.defaultString(enumName);
        if(enumName.contains(";")){
            String[] names = enumName.split(";");
            return names[names.length - 1];
        } else {
            return enumName;
        }
    }

    private String getFileExtention(MultipartFile file) {

        String[] parts = file.getOriginalFilename().split("\\.");
        if (parts.length != 0) {
            return parts[parts.length - 1];
        }
        return "";
    }
}
