/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2013
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4che3.conf.json.imageio;

import java.util.List;
import java.util.Map.Entry;

import javax.json.stream.JsonParser;

import org.dcm4che3.conf.json.ConfigurationDelegate;
import org.dcm4che3.conf.json.JsonConfigurationExtension;
import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.imageio.codec.ImageWriterFactory;
import org.dcm4che3.imageio.codec.ImageWriterFactory.ImageWriterParam;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.imageio.ImageWriterExtension;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2016
 */
public class JsonImageWriterConfiguration extends JsonConfigurationExtension {
    @Override
    protected void storeTo(Device device, JsonWriter writer) {
        ImageWriterExtension ext = device.getDeviceExtension(ImageWriterExtension.class);
        if (ext == null)
            return;

        writer.writeStartArray("dcmImageWriter");
        for (Entry<String, List<ImageWriterParam>> entry : ext.getImageWriterFactory().getEntries()) {
            writer.writeStartObject();
            String tsuid = entry.getKey();
            ImageWriterParam param = ImageWriterFactory.getImageWriterParam(entry.getValue());
            writer.writeNotNull("dicomTransferSyntax", tsuid);
            writer.writeNotNull("dcmIIOFormatName", param.formatName);
            writer.writeNotNull("dcmIIOName", param.name);
            writer.writeNotNull("dcmJavaClassName", param.className);
            writer.writeNotNull("dcmPatchJPEGLS", param.patchJPEGLS);
            writer.writeNotEmpty("dcmImageWriteParam", param.imageWriteParams);

            writer.writeEnd();
        }

        writer.writeEnd();
    }

    @Override
    public boolean loadDeviceExtension(Device device, JsonReader reader, ConfigurationDelegate config) {
        if (!reader.getString().equals("dcmImageWriter"))
            return false;

        ImageWriterFactory factory = new ImageWriterFactory();
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            String tsuid = null;
            String formatName = null;
            String name = null;
            String className = null;
            String patchJPEGLS = null;
            String[] imageWriteParam = new String[0];
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dicomTransferSyntax":
                        tsuid = reader.stringValue();
                        break;
                    case "dcmIIOFormatName":
                        formatName = reader.stringValue();
                        break;
                    case "dcmIIOName":
                        name = reader.stringValue();
                        break;
                    case "dcmJavaClassName":
                        className = reader.stringValue();
                        break;
                    case "dcmPatchJPEGLS":
                        patchJPEGLS = reader.stringValue();
                        break;
                    case "dcmImageWriteParam":
                        imageWriteParam = reader.stringArray();
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            factory.put(tsuid,
                new ImageWriterFactory.ImageWriterParam(formatName, className, patchJPEGLS, imageWriteParam, name));
        }
        device.addDeviceExtension(new ImageWriterExtension(factory));
        return true;
    }
}
