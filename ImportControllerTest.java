/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Lennart Pahl on behalf of all authors
 * Date: 2024-07-03
 */

package org.rapla.plugin.wwi2021;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.logger.Logger;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the ImportController class.
 */
@RunWith(MockitoJUnitRunner.class)
public class ImportControllerTest {

    @Mock
    private RaplaFacade facade;

    @Mock
    private Logger logger;

    @Mock
    private RemoteSession session;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private PrintWriter writer;

    @InjectMocks
    private ImportController importController;

    private User mockUser;
    private ICSFileUploadForm form;

    /**
     * Set up the test environment before each test.
     *
     * @throws Exception if an error occurs during setup
     */
    @Before
    public void setUp() throws Exception {
        // Initialize the mock objects
        MockitoAnnotations.openMocks(this);

        // Setup mock user
        mockUser = mock(User.class);
        when(mockUser.getUsername()).thenReturn("semesterplaner");

        // Initialize form
        form = new ICSFileUploadForm();

        // Mock the PrintWriter
        when(response.getWriter()).thenReturn(writer);
    }

    /**
     * Test successful import of a semester plan.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testImportSemesterplan_Success() throws Exception {
        when(session.checkAndGetUser(request)).thenReturn(mockUser);
        InputStream icsStream = new ByteArrayInputStream("BEGIN:VCALENDAR\nEND:VCALENDAR".getBytes());
        form.setIcsFile(icsStream);

        importController.importSemesterplan(request, response, form);

        // Verify interactions
        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(writer).write("Import successful");
        verify(writer).close();
    }

    /**
     * Test unauthorized access when importing a semester plan.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testImportSemesterplan_Unauthorized() throws Exception {
        when(session.checkAndGetUser(request)).thenThrow(new RaplaSecurityException("Unauthorized"));
        InputStream icsStream = new ByteArrayInputStream("BEGIN:VCALENDAR\nEND:VCALENDAR".getBytes());
        form.setIcsFile(icsStream);

        importController.importSemesterplan(request, response, form);

        // Verify interactions
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(logger, times(1)).error(anyString(), any(RaplaSecurityException.class));
        verify(writer).close();
    }

    /**
     * Test handling of an invalid ICS file during import.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testImportSemesterplan_InvalidICS() throws Exception {
        when(session.checkAndGetUser(request)).thenReturn(mockUser);
        // Provide a malformed ICS content
        InputStream icsStream = new ByteArrayInputStream("INVALID ICS CONTENT".getBytes());
        form.setIcsFile(icsStream);

        importController.importSemesterplan(request, response, form);

        // Verify interactions
        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        verify(logger).error(eq("Error processing the ICS file"), any(Exception.class));
        verify(writer).write("Internal server error");
        verify(writer).close();
    }

    /**
     * Test the importAppointmentsFromIcs method to ensure correct accumulation of entries with the same X-RAPLA-ID.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testImportAppointmentsFromIcs() throws Exception {
        String icsContent = "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "DTSTART:20210702T120000Z\n" +
                "DTEND:20210702T130000Z\n" +
                "X-RAPLA-ID:1\n" +
                "END:VEVENT\n" +
                "BEGIN:VEVENT\n" +
                "DTSTART:20210703T120000Z\n" +
                "DTEND:20210703T130000Z\n" +
                "X-RAPLA-ID:1\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";

        when(facade.newAppointmentWithUser(any(Date.class), any(Date.class), any(User.class))).thenReturn(mock(Appointment.class));
        when(facade.getUser(anyString())).thenReturn(mockUser);

        Map<ReferenceInfo<Reservation>, List<Appointment>> result = importController.importAppointmentsFromIcs(icsContent, "semesterplaner");

        assertNotNull(result);
        assertEquals(1, result.size());

        List<Appointment> appointments = result.values().iterator().next();
        assertEquals(2, appointments.size());
    }

    /**
     * Test the convertToDateWithUTCAdjustment method with an invalid date string.
     *
     * @throws ParseException if a parsing error occurs
     */
    @Test
    public void testConvertToDateWithUTCAdjustment_InvalidDate() throws ParseException {
        String timestamp = "invalid_date";
        Date date = importController.convertToDateWithUTCAdjustment(timestamp);

        assertNull(date);
    }

    /**
     * Test the convertToDateWithUTCAdjustment method with a valid date string during summer time.
     *
     * @throws ParseException if a parsing error occurs
     */
    @Test
    public void testConvertToDateWithUTCAdjustment_SummerTime() throws ParseException {
        String timestamp = "20210702T120000Z"; // July is in summer time (CEST)
        Date date = importController.convertToDateWithUTCAdjustment(timestamp);

        assertNotNull(date);
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTime(date);

        assertEquals(2021, calendar.get(Calendar.YEAR));
        assertEquals(Calendar.JULY, calendar.get(Calendar.MONTH));
        assertEquals(2, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(14, calendar.get(Calendar.HOUR_OF_DAY)); // 12 UTC + 2 hours for CEST
    }

    /**
     * Test the convertToDateWithUTCAdjustment method with a valid date string during winter time.
     *
     * @throws ParseException if a parsing error occurs
     */
    @Test
    public void testConvertToDateWithUTCAdjustment_WinterTime() throws ParseException {
        String timestamp = "20210102T120000Z"; // January is in winter time (CET)
        Date date = importController.convertToDateWithUTCAdjustment(timestamp);

        assertNotNull(date);
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTime(date);

        assertEquals(2021, calendar.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, calendar.get(Calendar.MONTH));
        assertEquals(2, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(13, calendar.get(Calendar.HOUR_OF_DAY)); // 12 UTC + 1 hour for CET
    }
}
