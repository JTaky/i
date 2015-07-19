package org.activiti.rest.controller;

import org.activiti.engine.form.FormProperty;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

/**
 * Created by taky on 7/19/15.
 */
public class TaskDownloadHeadersTest {

    private FormProperty formProperty;
    private FormProperty emptyFormProperty;

    @Before
    public void setUp(){
        formProperty = Mockito.mock(FormProperty.class);
        emptyFormProperty = Mockito.mock(FormProperty.class);
    }

    @Test
    public void testGetHeaders() throws Exception {
        assertArrayEquals("expected correct headers",
                new String[]{"bankIdPassport", "bIdLN bIdFN bIdMN", "", "1"},
                new TaskDownloadHeaders("${bankIdPassport};${bIdLN} ${bIdFN} ${dIdMN};;1")
                        .getHeaders(formProperty));
        assertArrayEquals("expected correct headers",
                new String[]{"bankIdPassport", "bIdLN bIdFN bIdMN", "", "1", ""},
                new TaskDownloadHeaders("${bankIdPassport};${bIdLN} ${bIdFN} ${dIdMN};;1;")
                        .getHeaders(formProperty));
        assertArrayEquals("expected correct headers",
                new String[]{},
                new TaskDownloadHeaders("${bankIdPassport};${bIdLN} ${bIdFN} ${dIdMN};;1;")
                        .getHeaders(emptyFormProperty));
    }

    @Test
    public void testGetValues() throws Exception {

    }
}