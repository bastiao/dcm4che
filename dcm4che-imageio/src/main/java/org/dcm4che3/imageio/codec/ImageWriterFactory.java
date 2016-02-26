/* ***** BEGIN LICENSE BLOCK *****
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
 * Agfa Healthcare.
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
 * ***** END LICENSE BLOCK ***** */

package org.dcm4che3.imageio.codec;

import org.dcm4che3.data.UID;
import org.dcm4che3.imageio.codec.ImageWriterFactory.ImageWriterParam;
import org.dcm4che3.imageio.codec.jpeg.PatchJPEGLS;
import org.dcm4che3.util.Property;
import org.dcm4che3.util.ResourceLocator;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * 
 */
public class ImageWriterFactory implements Serializable {

    private static final long serialVersionUID = 6328126996969794374L;

    private static final Logger LOG = LoggerFactory.getLogger(ImageWriterFactory.class);

    public static class ImageWriterParam implements Serializable {

        private static final long serialVersionUID = 3521737269113651910L;

        public final String formatName;
        public final String className;
        public final PatchJPEGLS patchJPEGLS;
        public final Property[] imageWriteParams;
        public final String name;


        public ImageWriterParam(String formatName, String className,
                PatchJPEGLS patchJPEGLS, Property[] imageWriteParams, String name) {
            if (formatName == null || name == null)
                throw new IllegalArgumentException();
            this.formatName = formatName;
            this.className = nullify(className);
            this.patchJPEGLS = patchJPEGLS;
            this.imageWriteParams = imageWriteParams;
            this.name = name;
        }

        public ImageWriterParam(String formatName, String className,
                String patchJPEGLS, String[] imageWriteParams, String name) {
            this(formatName, className, patchJPEGLS != null
                    && !patchJPEGLS.isEmpty() ? PatchJPEGLS
                    .valueOf(patchJPEGLS) : null, Property
                    .valueOf(imageWriteParams), name);
        }


        public Property[] getImageWriteParams() {
            return imageWriteParams;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ImageWriterParam that = (ImageWriterParam) o;

            if (!formatName.equals(that.formatName)) return false;
            if (className != null ? !className.equals(that.className) : that.className != null) return false;
            if (patchJPEGLS != that.patchJPEGLS) return false;
            return Arrays.equals(imageWriteParams, that.imageWriteParams);

        }

        @Override
        public int hashCode() {
            int result = formatName.hashCode();
            result = 31 * result + (className != null ? className.hashCode() : 0);
            result = 31 * result + (patchJPEGLS != null ? patchJPEGLS.hashCode() : 0);
            result = 31 * result + Arrays.hashCode(imageWriteParams);
            return result;
        }

        @Override
        public String toString() {
            return name;
        }
    }
    
    public static class ImageWriterItem {

        private final ImageWriter imageWriter;
        private final ImageWriterParam imageWriterParam;

        public ImageWriterItem(ImageWriter imageReader, ImageWriterParam imageReaderParam) {
            this.imageWriter = imageReader;
            this.imageWriterParam = imageReaderParam;
        }

        public ImageWriter getImageWriter() {
            return imageWriter;
        }

        public ImageWriterParam getImageWriterParam() {
            return imageWriterParam;
        }
    }

    private static String nullify(String s) {
        return s == null || s.isEmpty() || s.equals("*") ? null : s;
    }

    private static volatile ImageWriterFactory defaultFactory;

    private PatchJPEGLS patchJPEGLS;
    private Map<String, List<ImageWriterParam>> mapTransferSyntaxUIDs = new LinkedHashMap<String, List<ImageWriterParam>>();

    public static ImageWriterFactory getDefault() {
        if (defaultFactory == null)
            defaultFactory = initDefault();

        return defaultFactory;
    }

    public static void resetDefault() {
        defaultFactory = null;
    }

    public static void setDefault(ImageWriterFactory factory) {
        if (factory == null)
            throw new NullPointerException();

        defaultFactory = factory;
    }

    private static ImageWriterFactory initDefault() {
        ImageWriterFactory factory = new ImageWriterFactory();
        String name = System.getProperty(ImageWriterFactory.class.getName(),
                "org/dcm4che3/imageio/codec/ImageWriterFactory.xml");
        try {
            factory.load(name);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load Image Writer Factory configuration from: "
                            + name, e);
        }
        factory.init();

        return factory;
    }

    public void init() {
        if (LOG.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Image Writers:\n");
            for (Entry<String, List<ImageWriterParam>> entry : mapTransferSyntaxUIDs.entrySet()) {
                String tsUid = entry.getKey();
                sb.append(' ').append(tsUid);
                sb.append(" (").append(UID.nameOf(tsUid)).append("): ");
                for(ImageWriterParam reader : entry.getValue()){
                    sb.append(reader.name);
                    sb.append(' ');
                }
                sb.append('\n');
            }
            LOG.debug(sb.toString());
        }
    }
    
    public void load(String name) throws IOException {
        URL url;
        try {
            url = new URL(name);
        } catch (MalformedURLException e) {
            url = ResourceLocator.getResourceURL(name, this.getClass());
            if (url == null)
                throw new IOException("No such resource: " + name);
        }
        InputStream in = url.openStream();
        try {
            load(in);
        } finally {
            SafeClose.close(in);
        }
    }

    public void load(InputStream stream) throws IOException {
        XMLStreamReader xmler = null;
        try {
            String sys = ImageReaderFactory.getNativeSystemSpecification();
            
            XMLInputFactory xmlif = XMLInputFactory.newInstance();
            xmler = xmlif.createXMLStreamReader(stream);

            int eventType;
            while (xmler.hasNext()) {
                eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        String key = xmler.getName().getLocalPart();
                        if ("ImageWriterFactory".equals(key)) {
                            while (xmler.hasNext()) {
                                eventType = xmler.next();
                                switch (eventType) {
                                    case XMLStreamConstants.START_ELEMENT:
                                        key = xmler.getName().getLocalPart();
                                        if ("element".equals(key)) {
                                            String tsuid = xmler.getAttributeValue(null, "tsuid");
                                           
                                            boolean state = true;
                                            while (xmler.hasNext() && state) {
                                                eventType = xmler.next();
                                                switch (eventType) {
                                                    case XMLStreamConstants.START_ELEMENT:
                                                        key = xmler.getName().getLocalPart();
                                                        if ("writer".equals(key)) {
                                                            String s = xmler.getAttributeValue(null, "sys");
                                                            String[] systems = s == null ? null : s.split(",");
                                                            if (systems == null
                                                                || (sys != null && Arrays.binarySearch(systems, sys) >= 0)) {
                                                                // Only add readers that can run on the current system
                                                                ImageWriterParam param =
                                                                    new ImageWriterParam(xmler.getAttributeValue(null,
                                                                        "format"), xmler.getAttributeValue(null,
                                                                        "class"), xmler.getAttributeValue(null,
                                                                        "patchJPEGLS"), StringUtils.split(
                                                                        xmler.getAttributeValue(null, "params"), ';'),
                                                                        xmler.getAttributeValue(null, "name"));
                                                                if(tsuid != null){
                                                                	put(tsuid, param);
                                                                }
                                                            }
                                                        }
                                                        break;
                                                    case XMLStreamConstants.END_ELEMENT:
                                                        if ("element".equals(xmler.getName().getLocalPart())) {
                                                            state = false;
                                                        }
                                                        break;
                                                    default:
                                                        break;
                                                }
                                            }
                                        }
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        catch (XMLStreamException e) {
            LOG.error("Cannot read DICOM Writers! " + e.getMessage());
        } finally {
            if (xmler != null) {
                try {
                    xmler.close();
                } catch (XMLStreamException e) {
                    LOG.debug(e.getMessage());
                }
            }
            SafeClose.close(stream);
        }
    }


    public final PatchJPEGLS getPatchJPEGLS() {
        return patchJPEGLS;
    }

    public final void setPatchJPEGLS(PatchJPEGLS patchJPEGLS) {
        this.patchJPEGLS = patchJPEGLS;
    }

    public List<ImageWriterParam> get(String tsuid) {
        return mapTransferSyntaxUIDs.get(tsuid);
    }

    public boolean put(String tsuid, ImageWriterParam param) {
        List<ImageWriterParam> writerSet = get(tsuid);
        if (writerSet == null) {
            writerSet = new ArrayList<ImageWriterParam>();
            mapTransferSyntaxUIDs.put(tsuid, writerSet);
        }
        return writerSet.add(param);
    }

    public Set<Entry<String, List<ImageWriterParam>>> getEntries() {
        return Collections.unmodifiableMap(mapTransferSyntaxUIDs).entrySet();
    }
    

    public static ImageWriterItem getImageWriterParam(String tsuid) {
        List<ImageWriterParam> list = getDefault().get(tsuid);
        if (list != null) {
            synchronized (list) {
                for (Iterator<ImageWriterParam> it = list.iterator(); it.hasNext();) {
                    ImageWriterParam imageParam = it.next();
                    String cl = imageParam.className;
                    Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName(imageParam.formatName);
                    while (iter.hasNext()) {
                        ImageWriter writer = iter.next();
                        if (cl == null || writer.getClass().getName().equals(cl)) {
                            return new ImageWriterItem(writer, imageParam);
                        }
                    }
                }
            }
        }
        return null;
    }
    
    public static ImageWriterParam getImageWriterParam(List<ImageWriterParam> list) {
        if (list != null) {
            for (Iterator<ImageWriterParam> it = list.iterator(); it.hasNext();) {
                ImageWriterParam imageParam = it.next();
                String cl = imageParam.className;
                Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName(imageParam.formatName);
                while (iter.hasNext()) {
                    ImageWriter writer = iter.next();
                    if (cl == null || writer.getClass().getName().equals(cl)) {
                        return imageParam;
                    }
                }
            }
        }
        return null;
    }
    
    public static ImageWriter getImageWriter(ImageWriterParam param) {
        return Boolean.getBoolean("org.dcm4che3.imageio.codec.useServiceLoader")
                ? getImageWriterFromServiceLoader(param)
                : getImageWriterFromImageIOServiceRegistry(param);
    }

    public static ImageWriter getImageWriterFromImageIOServiceRegistry(ImageWriterParam param) {
        Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName(param.formatName);
        if (!iter.hasNext())
            throw new RuntimeException("No Writer for format: " + param.formatName + " registered");

        ImageWriter writer = iter.next();
        if (param.className != null) {
            while (!param.className.equals(writer.getClass().getName())) {
                if (iter.hasNext())
                    writer = iter.next();
                else {
                    LOG.warn("No preferred Writer {} for format: {} - use {}",
                            param.className, param.formatName, writer.getClass().getName());
                    break;
                }
            }
        }
        return writer;
    }

    public static ImageWriter getImageWriterFromServiceLoader(ImageWriterParam param) {
        try {
            return getImageWriterSpi(param).createWriterInstance();
        } catch (IOException e) {
            throw new RuntimeException("Error instantiating Writer for format: "  + param.formatName, e);
        }
    }

    private static ImageWriterSpi getImageWriterSpi(ImageWriterParam param) {
        Iterator<ImageWriterSpi> iter = new FormatNameFilterIterator<ImageWriterSpi>(
                ServiceLoader.load(ImageWriterSpi.class).iterator(), param.formatName);
        if (!iter.hasNext())
            throw new RuntimeException("No Writer for format: " + param.formatName + " registered");

        ImageWriterSpi spi = iter.next();
        if (param.className != null) {
            while (!param.className.equals(spi.getPluginClassName())) {
                if (iter.hasNext())
                    spi = iter.next();
                else {
                    LOG.warn("No preferred Writer {} for format: {} - use {}",
                            param.className, param.formatName, spi.getPluginClassName());
                    break;
                }
            }
        }
        return spi;
    }

}
