/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.util;

import org.apache.batik.test.AbstractTest;
import org.apache.batik.test.DefaultTestReport;
import org.apache.batik.test.TestReport;

import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * This test validates that the ParsedURL class properly parses and
 * cascades URLs.
 *
 * @author <a href="mailto:deweese@apache.org">Thomas DeWeese</a>
 * @version $Id$
 */
public class ParsedURLTest extends AbstractTest {
    /**
     * Error when unable to parse URL
     * {0} = 'Base URL' or NULL
     * {1} = Sub URL
     * {2} = exception stack trace.
     */
    public static final String ERROR_CANNOT_PARSE_URL
        = "ParsedURLTest.error.cannot.parse.url";

    /**
     * Result didn't match expected result.
     * {0} = result
     * {1} = expected result
     */
    public static final String ERROR_WRONG_RESULT
        = "ParsedURLTest.error.wrong.result";

    public static final String ENTRY_KEY_ERROR_DESCRIPTION
        = "ParsedURLTest.entry.key.error.description";

    protected String base = null;
    protected String sub  = null;
    protected String ref  = null;
    /**
     * Constructor
     * @param url The url to parse
     * @param ref The expected result.
     */
    public ParsedURLTest(String url, String ref ){
        this.base = url;
        this.ref  = ref;
    }

    /**
     * Constructor
     * @param base The base url to parse
     * @param sub The sub url (relative to base).
     * @param ref The expected result.
     */
    public ParsedURLTest(String base, String sub, String ref){
        this.base = base;
        this.sub  = sub;
        this.ref  = ref;
    }

    /**
     * Returns this Test's name
     */
    public String getName() {
        return ref + " -- " + super.getName();
    }

    /**
     * This method will only throw exceptions if some aspect
     * of the test's internal operation fails.
     */
    public TestReport runImpl() throws Exception {
        DefaultTestReport report
            = new DefaultTestReport(this);

        ParsedURL url;
        try {
            url = new ParsedURL(base);
            if (sub != null) {
                url  = new ParsedURL(url, sub);
            }
        } catch(Exception e) {
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            report.setErrorCode(ERROR_CANNOT_PARSE_URL);
            report.setDescription(new TestReport.Entry[] {
                new TestReport.Entry
                    (Messages.formatMessage(ENTRY_KEY_ERROR_DESCRIPTION, null),
                     Messages.formatMessage
                     (ERROR_CANNOT_PARSE_URL,
                      new String[]{sub == null? "null" : base, 
                                   sub,
                                   trace.toString()}))
                    });
            report.setPassed(false);
            return report;
        }

        if (ref.equals(url.toString())) {
            report.setPassed(true);
            return report;
        }

        report.setErrorCode(ERROR_WRONG_RESULT);
        report.setDescription(new TestReport.Entry[] {
          new TestReport.Entry
            (Messages.formatMessage(ENTRY_KEY_ERROR_DESCRIPTION, null),
             Messages.formatMessage
             (ERROR_WRONG_RESULT, new String[]{url.toString(), ref }))
            });
        report.setPassed(false);
        return report;
    }
}
