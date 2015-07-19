package org.activiti.rest.controller;

import org.activiti.engine.form.FormProperty;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Util class in order to format headers for
 * {@link ActivitiRestApiController#downloadTasksData(String, String, String, String, String, String, String, Date, Date, Integer, Integer, HttpServletRequest, HttpServletResponse)}
 *
 * Created by taky on 7/19/15.
 */
public class TaskDownloadHeaders {

    private static final Pattern pattern = Pattern.compile("(\\d+|\\$\\{\\w+\\})+");

    List<String> columns = new ArrayList<>();
    List<String> headers = new ArrayList<>();

    List<List<String>> fieldsToAccess = new ArrayList<>();
    List<String> corruptedPartitions = new ArrayList<>();

    public TaskDownloadHeaders(String fields){
        List<String> columns = Arrays.asList(fields.split(";"));
        for(String col : columns){
            fieldsToAccess.add(parseCol(col.trim()));
        }
    }

    private List<String> parseCol(String col) {
        List<String> matchedFieldNames = new ArrayList<>();
        Matcher matcher = pattern.matcher(col);
        if(matcher.find()){
            for (int i = 0; i < matcher.groupCount(); i++)
            {
                String match = matcher.group(i);
                if(!StringUtils.isBlank(match)){
                    //EXTRACT from string if need
                    matchedFieldNames.add(match);
                }
            }
        } else {
            corruptedPartitions.add(col);
        }
        return matchedFieldNames;
    }

    public String[] getHeaders(FormProperty property) {
        List<String> headers = new ArrayList<>();
//        for(){
//
//        }
        return headers.toArray(new String[0]);
    }

    public Collection<? extends String> getValues(FormProperty property) {
        return null;
    }

}
