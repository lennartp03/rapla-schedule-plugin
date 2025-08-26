package org.rapla.plugin.wwi2021;

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

import javax.ws.rs.FormParam;
import javax.ws.rs.core.MediaType;

import org.jboss.resteasy.annotations.providers.multipart.PartType;
import java.io.InputStream;

/**
 * ICSFileUploadForm is a form used to upload ICS files.
 * This class represents the structure of the multipart form data
 * for uploading ICS files in a RESTful API.
 */
public class ICSFileUploadForm {

    /**
     * The InputStream of the uploaded ICS file.
     */
    @FormParam("file")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    private InputStream icsFile;

    /**
     * Gets the InputStream of the uploaded ICS file.
     *
     * @return the InputStream of the ICS file
     */
    public InputStream getIcsFile() {
        return icsFile;
    }

    /**
     * Sets the InputStream of the uploaded ICS file.
     *
     * @param icsFile the InputStream of the ICS file
     */
    public void setIcsFile(InputStream icsFile) {
        this.icsFile = icsFile;
    }
}
