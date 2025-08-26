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

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.*;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * ImportController handles the import of semester plans from ICS files.
 */
@Singleton
@Path("semesterplan")
public class ImportController {
    @Inject
    public RaplaFacade facade;

    @Inject
    public Logger logger;

    @Inject
    RemoteSession session;

    @Inject
    public ImportController(@Context HttpServletRequest request){
    }

    /**
     * Endpoint logic for importing a semester plan from an ICS file.
     *
     * @param req   the HTTP request
     * @param res   the HTTP response
     * @param form  the form containing the ICS file
     * @throws Exception if an error occurs during the import
     */
    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_PLAIN)
    public void importSemesterplan(@Context HttpServletRequest req, @Context HttpServletResponse res, @MultipartForm ICSFileUploadForm form) throws Exception {
        InputStream icsInputStream = null;
        User user;
        String userName;
        List<String> failedReservationIds = new ArrayList<>();

        try {
            // Check and get the user from the session
            user = session.checkAndGetUser(req);
            userName = user.getUsername();
        } catch (RaplaSecurityException sec) {
            logger.error("Unauthorized access: No user found in session.", sec);
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            generatePage(res, res.getStatus());
            return;
        }

        try {
            // Get the ICS file input stream
            icsInputStream = form.getIcsFile();
            String ics = convertStreamToString(icsInputStream);

            // Process the ICS file and update reservations
            Map<ReferenceInfo<Reservation>, List<Appointment>> result = importAppointmentsFromIcs(ics, userName);
            List<Reservation> reservationsToStore = new ArrayList<>();

            for (Map.Entry<ReferenceInfo<Reservation>, List<Appointment>> entry : result.entrySet()) {
                try {
                    logger.info(entry.getKey().getId());
                    processReservation(entry, reservationsToStore);
                } catch (Exception e) {
                    failedReservationIds.add(entry.getKey().getId());
                    logger.error("Error processing reservation - wrong id: " + entry.getKey().getId());
                }
            }

            // Store all reservations at once
            Entity[] events = reservationsToStore.toArray(Reservation.RESERVATION_ARRAY);
            facade.storeAndRemove(events, new Entity[]{}, user);

            // Log summary of failed reservations
            if (!failedReservationIds.isEmpty()) {
                logger.warn("Failed to resolve the following reservation IDs: " + String.join(", ", failedReservationIds));
            }

            // Set successful response status
            res.setStatus(HttpServletResponse.SC_OK);
            res.getWriter().write("Import successful");
        } catch (RaplaSecurityException e) {
            logger.error("User doesn't have enough rights for storing the ICS file", e);
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.getWriter().write("Forbidden: insufficient rights");
        } catch (Exception e) {
            logger.error("Error processing the ICS file", e);
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            res.getWriter().write("Internal server error");
        } finally {
            if (icsInputStream != null) {
                icsInputStream.close();
            }
            // Optionally generating a web page displaying the results
            generatePage(res, res.getStatus());
        }
    }

    /**
     * Processes a reservation by removing placeholder appointments and adding recently parsed appointments.
     *
     * @param entry                 the entry containing the reservation reference and appointments
     * @param reservationsToStore   the list of reservations to store
     * @throws RaplaException       if an error occurs during processing
     */
    private void processReservation(Map.Entry<ReferenceInfo<Reservation>, List<Appointment>> entry, List<Reservation> reservationsToStore) throws RaplaException {
        Reservation reservation;
        ReferenceInfo<Reservation> reservationId = entry.getKey();
        List<Appointment> appointments = entry.getValue();

        try {
            reservation = facade.edit(facade.resolve(reservationId));
        } catch (EntityNotFoundException e) {
            logger.error("Module id not found");
            return;
        }

        // Remove placeholder appointments and add new ones
        for (Appointment appointment : reservation.getAppointments()) {
            reservation.removeAppointment(appointment);
        }
        for (Appointment appointment : appointments) {
            reservation.addAppointment(appointment);
        }

        reservationsToStore.add(reservation);
        logger.info("Successfully added reservation appointments for id " + reservationId + " from imported ics-File");
    }

    /**
     * Converting the ICS contents to a suitable format and logical parsing of appointments from the ICS file.
     *
     * @param icsFile the content of the ICS file
     * @param userName the username of the user
     * @return a map of reservations and their corresponding appointments
     * @throws RaplaException, ParseException, ParserException, IOException if an error occurs during import
     */
    public Map<ReferenceInfo<Reservation>, List<Appointment>> importAppointmentsFromIcs(String icsFile, String userName) throws RaplaException, ParseException, ParserException, IOException {
        Map<ReferenceInfo<Reservation>, List<Appointment>> newMap = new LinkedHashMap<>();
        StringReader sin = new StringReader(icsFile);
        CalendarBuilder builder = new CalendarBuilder();
        net.fortuna.ical4j.model.Calendar calendar = builder.build(sin);

        // Temporary map to group appointments by X-RAPLA-ID
        Map<String, List<Appointment>> tempMap = new HashMap<>();

        for (Component event : calendar.getComponents(Component.VEVENT)) {
            // Extract DTSTART, DTEND, and X-RAPLA-ID
            String start = event.getProperty(Property.DTSTART).getValue();
            String end = event.getProperty(Property.DTEND).getValue();
            String raplaId = event.getProperty("X-RAPLA-ID").getValue();

            Date startDate = convertToDateWithUTCAdjustment(start);
            Date endDate = convertToDateWithUTCAdjustment(end);

            // Create a new appointment with the start and end dates
            Appointment appointment = facade.newAppointmentWithUser(startDate, endDate, facade.getUser(userName));

            // Group appointments by X-RAPLA-ID
            tempMap.computeIfAbsent(raplaId, k -> new ArrayList<>()).add(appointment);
        }

        // Convert grouped appointments into the final map format
        for (Map.Entry<String, List<Appointment>> entry : tempMap.entrySet()) {
            String raplaId = entry.getKey();
            List<Appointment> appointments = entry.getValue();

            // Convert raplaId to ReferenceInfo<Reservation>
            ReferenceInfo<Reservation> refInfo = new ReferenceInfo<>(raplaId, Reservation.class);
            newMap.put(refInfo, appointments);
        }
        return newMap;
    }

    /**
     * Converts a timestamp string to a Date object in UTC time zone, adjusting for DST.
     *
     * @param timestamp the timestamp string
     * @return the converted Date object
     */
    public Date convertToDateWithUTCAdjustment(String timestamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(timestamp, formatter);
            ZonedDateTime utcDateTime = localDateTime.atZone(ZoneId.of("UTC"));
            ZonedDateTime berlinDateTime = utcDateTime.withZoneSameInstant(ZoneId.of("Europe/Berlin"));

            // Check if the date is in DST in the Europe/Berlin time zone
            boolean isDST = berlinDateTime.getZone().getRules().isDaylightSavings(berlinDateTime.toInstant());

            // Adjust the UTC time by +1 or +2 hours
            ZonedDateTime adjustedUtcDateTime = utcDateTime.plusHours(isDST ? 2 : 1);

            return Date.from(adjustedUtcDateTime.toInstant());
        } catch (DateTimeParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generates an HTML response page.
     *
     * @param res the HTTP response
     * @param responseCode the HTTP response code
     * @throws IOException if an error occurs while writing the response
     */
    public void generatePage(HttpServletResponse res, int responseCode) throws IOException {
        res.setContentType("text/html;charset=UTF-8");
        PrintWriter out = res.getWriter();

        String title, heading, message, color;
        if (responseCode == HttpServletResponse.SC_OK) {
            title = "Import Erfolg";
            heading = "Erfolgreich hinzugefügt";
            message = "Der Semesterplan wurde erfolgreich importiert";
            color = "#4CAF50"; // Green color for success
        } else if (responseCode == HttpServletResponse.SC_UNAUTHORIZED) {
            title = "Zugriff verweigert";
            heading = "Nicht autorisiert";
            message = "Sie sind nicht berechtigt, diese Aktion auszuführen.";
            color = "#f44336"; // Red color for error
        } else {
            title = "Import fehlerhaft";
            heading = "Fehler erkannt - Fehlercode: " + responseCode;
            message = "Beim Import des Semesterplans ist ein Fehler aufgetreten.";
            color = "#4a90e2"; // Blue color for error
        }

        out.println("<html>");
        out.println("<head>");
        out.println("  <title>" + title + "</title>");
        out.println("  <style>");
        out.println("    body { font-family: Arial, sans-serif; background-color: #f4f4f9; padding: 20px; color: #7c898f; }");
        out.println("    .container { max-width: 600px; margin: auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); text-align: center; }");
        out.println("    h1 { color: " + color + "; }");
        out.println("    .button { background-color: " + color + "; color: white; padding: 10px 20px; border: none; border-radius: 4px; cursor: pointer; font-weight: bold; margin-top: 20px; text-decoration: none; display: inline-block; transition: background-color 0.3s ease; }");
        out.println("    .button:hover { background-color: #357ABD; }"); // Hover color to a darker blue
        out.println("  </style>");
        out.println("</head>");
        out.println("<body>");
        out.println("  <div class='container'>");
        out.println("    <h1>" + heading + "</h1>");
        out.println("    <p>" + message + "</p>");
        out.println("    <a href='/rapla/semesterplan' class='button'>Zurück zur Startseite</a>");
        out.println("  </div>");
        out.println("</body>");
        out.println("</html>");

        out.close();
    }

    /**
     * Converts an InputStream to a String.
     *
     * @param is the InputStream
     * @return the resulting String
     * @throws IOException if an error occurs during conversion
     */
    private String convertStreamToString(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }


}
