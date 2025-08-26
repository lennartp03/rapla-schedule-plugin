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

import org.rapla.RaplaSystemInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.logger.Logger;
import org.rapla.server.internal.ServerContainerContext;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Singleton
@Path("semesterplan")
public class RaplaImportSemesterplan{
    @Inject
    public RaplaFacade facade;

    @Inject
    public Logger logger;
    @Inject RaplaSystemInfo m_i18n;
    @Inject ServerContainerContext serverContainerContext;
    @Inject
    public RaplaImportSemesterplan()
    {
    }


    @GET
    @Produces(MediaType.TEXT_HTML)
    public void generatePage( @Context HttpServletRequest request, @Context HttpServletResponse response ) throws Exception {
        java.io.PrintWriter out = response.getWriter();
        out.println("<html>");
        out.println("<head>");
        out.println("  <title>Importiere Semesterplan (.ics)</title>");
        out.println("  <style>");
        out.println("    body { font-family: Arial, sans-serif; background-color: #f4f4f9; padding: 20px; color: #7c898f; position: relative; min-height: 100vh; }");
        out.println("    .container { max-width: 600px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }");
        out.println("    h1 { text-align: center; color: #4a90e2; }");
        out.println("    .upload-area { border: 2px dashed #7c898f; border-radius: 4px; padding: 40px 20px; text-align: center; background-color: #f9f9f9; position: relative; }");
        out.println("    .upload-area:hover { border-color: #4a90e2; }");
        out.println("    .upload-area label { cursor: pointer; color: #4a90e2; font-weight: bold; }");
        out.println("    .upload-area.dragging { border-color: #4a90e2; background-color: #f4f4f9; }");
        out.println("    .upload-icon { font-size: 48px; color: #7c898f; }");
        out.println("    input[type='file'] { display: none; }");
        out.println("    input[type='submit'] { background-color: #7c898f; color: white; padding: 10px 20px; border: none; border-radius: 4px; cursor: pointer; font-weight: bold; margin-top: 20px; display: block; width: 100%; transition: background-color 0.3s ease; }");
        out.println("    input[type='submit']:hover { background-color: #4a90e2; }");
        out.println("    .info { text-align: center; margin-top: 20px; color: #7c898f; }");
        out.println("    .footer { position: absolute; bottom: 40px; right: 20px; text-align: center; }");
        out.println("    .footer img { max-width: 100px; }");
        out.println("    .footer p { color: #7c898f; margin-top: 5px; font-size: 12px; }");
        out.println("  </style>");
        out.println("</head>");

        out.println("<body>");
        out.println("  <div class='container'>");
        out.println("    <h1>Importiere hier den Semesterplan</h1>");
        out.println("    <form action='semesterplan/import' method='POST' enctype='multipart/form-data'>");
        out.println("      <div class='upload-area' id='uploadfile'>");
        out.println("        <input type='file' id='file' name='file' />");
        out.println("        <label for='file'><span class='upload-icon'>&#x1F4E5;</span><br>Drag & Drop deine Datei hier oder klicke zum Hochladen</label>");
        out.println("      </div>");
        out.println("      <input type='submit' value='Hochladen'>");
        out.println("    </form>");
        out.println("    <div class='info'>Bitte lade eine gültige .ics oder .ical Datei hoch</div>");
        out.println("  </div>");
        out.println("  <div class='footer'>");
        out.println("    <img src='path/to/dhbw_logo.png' alt='DHBW Logo' />");
        out.println("    <p>Made by students of DHBW</p>");
        out.println("  </div>");
        out.println("  <script>");
        out.println("    var uploadArea = document.getElementById('uploadfile');");
        out.println("    var fileInput = document.getElementById('file');");
        out.println("    var form = document.querySelector('form');");
        out.println("    var label = document.querySelector('label[for=\"file\"]');");

        out.println("    uploadArea.addEventListener('dragover', function(e) {");
        out.println("      e.preventDefault();");
        out.println("      uploadArea.classList.add('dragging');");
        out.println("    });");

        out.println("    uploadArea.addEventListener('dragleave', function(e) {");
        out.println("      e.preventDefault();");
        out.println("      uploadArea.classList.remove('dragging');");
        out.println("    });");

        out.println("    uploadArea.addEventListener('drop', function(e) {");
        out.println("      e.preventDefault();");
        out.println("      uploadArea.classList.remove('dragging');");
        out.println("      var files = e.dataTransfer.files;");
        out.println("      if (validateFile(files[0])) {");
        out.println("        fileInput.files = files;");
        out.println("        label.textContent = files[0].name + ' ausgewählt';");
        out.println("        label.style.color = 'green';");
        out.println("      } else {");
        out.println("        alert('Bitte lade eine gültige .ics oder .ical Datei hoch');");
        out.println("        fileInput.value = '';");
        out.println("        label.innerHTML = '<span class=\"upload-icon\">&#x1F4E5;</span><br>Drag & Drop deine Datei hier oder klicke zum Hochladen';");
        out.println("        label.style.color = '#4a90e2';");
        out.println("      }");
        out.println("    });");

        out.println("    fileInput.addEventListener('change', function(e) {");
        out.println("      var file = fileInput.files[0];");
        out.println("      if (validateFile(file)) {");
        out.println("        label.textContent = file.name + ' ausgewählt';");
        out.println("        label.style.color = 'green';");
        out.println("      } else {");
        out.println("        alert('Bitte lade eine gültige .ics oder .ical Datei hoch');");
        out.println("        fileInput.value = '';");
        out.println("        label.innerHTML = '<span class=\"upload-icon\">&#x1F4E5;</span><br>Drag & Drop deine Datei hier oder klicke zum Hochladen';");
        out.println("        label.style.color = '#4a90e2';");
        out.println("      }");
        out.println("    });");

        out.println("    function validateFile(file) {");
        out.println("      var validExtensions = ['ics', 'ical'];");
        out.println("      var fileExtension = file.name.split('.').pop().toLowerCase();");
        out.println("      return validExtensions.includes(fileExtension);");
        out.println("    }");

        out.println("    form.addEventListener('submit', function(e) {");
        out.println("      if (fileInput.files.length === 0) {");
        out.println("        e.preventDefault();");
        out.println("        alert('Bitte wähle eine Datei zum Hochladen aus');");
        out.println("      }");
        out.println("    });");
        out.println("  </script>");
        out.println("</body>");
        out.println("</html>");
        out.close();
    }
}