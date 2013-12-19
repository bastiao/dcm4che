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
 * Portions created by the Initial Developer are Copyright (C) 2011
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

package org.dcm4che.tool.movescu;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.ElementDictionary;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Connection;
import org.dcm4che.net.Device;
import org.dcm4che.net.DimseRSPHandler;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.ExtendedNegotiation;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.tool.common.CLIUtils;
import org.dcm4che.tool.storescp.StoreSCP;
import org.dcm4che.util.SafeClose;
import org.dcm4che.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class MoveSCU extends Device {
    private static final Logger LOG = LoggerFactory.getLogger(MoveSCU.class);
    
    private static enum InformationModel {
        PatientRoot(UID.PatientRootQueryRetrieveInformationModelMOVE, "STUDY"),
        StudyRoot(UID.StudyRootQueryRetrieveInformationModelMOVE, "STUDY"),
        PatientStudyOnly(UID.PatientStudyOnlyQueryRetrieveInformationModelMOVERetired, "STUDY"),
        CompositeInstanceRoot(UID.CompositeInstanceRootRetrieveMOVE, "IMAGE"),
        HangingProtocol(UID.HangingProtocolInformationModelMOVE, null),
        ColorPalette(UID.ColorPaletteInformationModelMOVE, null);

        final String cuid;
        final String level;

        InformationModel(String cuid, String level) {
            this.cuid = cuid;
            this.level = level;
       }
    }

    private static ResourceBundle rb =
        ResourceBundle.getBundle("org.dcm4che.tool.movescu.messages");

    private static final int[] DEF_IN_FILTER = {
        Tag.SOPInstanceUID,
        Tag.StudyInstanceUID,
        Tag.SeriesInstanceUID
    };

    private final ApplicationEntity ae = new ApplicationEntity("MOVESCU");
    private final Connection conn = new Connection();
    private final Connection remote = new Connection();
    private final AAssociateRQ rq = new AAssociateRQ();
    private int priority;
    private String destination;
    private InformationModel model;
    private Attributes keys = new Attributes();
    private int[] inFilter = DEF_IN_FILTER;
    private Association as;
    
    private String logPath;
    private StringBuilder logBuilder;
    private String endLogLine;
    private long startRetrieve;
    private File storageDir;
   
    public MoveSCU() throws IOException {
        super("movescu");
        addConnection(conn);
        addApplicationEntity(ae);
        ae.addConnection(conn);
    }
    
    public final void setPriority(int priority) {
        this.priority = priority;
    }

    public final void setInformationModel(InformationModel model, String[] tss,
            boolean relational) {
       this.model = model;
       rq.addPresentationContext(new PresentationContext(1, model.cuid, tss));
       if (relational)
           rq.addExtendedNegotiation(new ExtendedNegotiation(model.cuid, new byte[]{1}));
       if (model.level != null)
           addLevel(model.level);
    }

    public void addLevel(String s) {
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, s);
    }

    public final void setDestination(String destination) {
        this.destination = destination;
    }

    public void addKey(int tag, String... ss) {
        VR vr = ElementDictionary.vrOf(tag, keys.getPrivateCreator(tag));
        keys.setString(tag, vr, ss);
    }

    public final void setInputFilter(int[] inFilter) {
        this.inFilter  = inFilter;
    }

    private static CommandLine parseComandLine(String[] args)
                throws ParseException {
            Options opts = new Options();
            addServiceClassOptions(opts);
            addKeyOptions(opts);
            addRetrieveLevelOption(opts);
            addDestinationOption(opts);
            CLIUtils.addLogOptions(opts);
            CLIUtils.addConnectOption(opts);
            CLIUtils.addBindOption(opts, "MOVESCU");
            CLIUtils.addAEOptions(opts);
            CLIUtils.addRetrieveTimeoutOption(opts);
            CLIUtils.addPriorityOption(opts);
            CLIUtils.addCommonOptions(opts);
            return CLIUtils.parseComandLine(args, opts, rb, MoveSCU.class);
    }

    @SuppressWarnings("static-access")
    private static void addRetrieveLevelOption(Options opts) {
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("PATIENT|STUDY|SERIES|IMAGE|FRAME")
                .withDescription(rb.getString("level"))
                .create("L"));
   }

    @SuppressWarnings("static-access")
    private static void addDestinationOption(Options opts) {
        opts.addOption(OptionBuilder
                .withLongOpt("dest")
                .hasArg()
                .withArgName("aet")
                .withDescription(rb.getString("dest"))
                .create());
        
    }

    @SuppressWarnings("static-access")
    private static void addKeyOptions(Options opts) {
        opts.addOption(OptionBuilder
                .hasArgs()
                .withArgName("attr=value")
                .withValueSeparator('=')
                .withDescription(rb.getString("match"))
                .create("m"));
        opts.addOption(OptionBuilder
                .hasArgs()
                .withArgName("attr")
                .withDescription(rb.getString("in-attr"))
                .create("i"));
    }

    @SuppressWarnings("static-access")
    private static void addServiceClassOptions(Options opts) {
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("name")
                .withDescription(rb.getString("model"))
                .create("M"));
        CLIUtils.addTransferSyntaxOptions(opts);
        opts.addOption(null, "relational", false, rb.getString("relational"));
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        try {
            CommandLine cl = parseComandLine(args);
            MoveSCU main = new MoveSCU();
            main.logPath = cl.getOptionValue("log-file");
            if (main.logPath != null) {
                String tempDir = System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
                File tdir;
                if (tempDir == null || tempDir.length() == 1) {
                    String dir = System.getProperty("user.home", ""); //$NON-NLS-1$ //$NON-NLS-2$
                    tdir = new File(dir, "dicoms");
                } else {
                    tdir = new File(tempDir, "dicoms");
                }
                main.storageDir = tdir;
                main.storageDir.mkdirs();
                
				if (!cl.hasOption("b"))
					throw new IllegalArgumentException(
							"-b configuration is missing!");

				StoreSCP.main(new String[] { "-b", cl.getOptionValue("b"),
						"--accept-unknown", "--directory",
						main.storageDir.getAbsolutePath() });

            }
            CLIUtils.configureConnect(main.remote, main.rq, cl);
            CLIUtils.configureBind(main.conn, main.ae, cl);
            CLIUtils.configure(main.conn, cl);
            main.remote.setTlsProtocols(main.conn.getTlsProtocols());
            main.remote.setTlsCipherSuites(main.conn.getTlsCipherSuites());
            configureServiceClass(main, cl);
            configureKeys(main, cl);
            main.setPriority(CLIUtils.priorityOf(cl));
            main.setDestination(destinationOf(cl));
            ExecutorService executorService =
                    Executors.newSingleThreadExecutor();
            ScheduledExecutorService scheduledExecutorService =
                    Executors.newSingleThreadScheduledExecutor();
            main.setExecutor(executorService);
            main.setScheduledExecutor(scheduledExecutorService);
            try {  
                if (main.logPath != null) {
                    CLIUtils.recursiveDelete(main.storageDir, false, LOG);
                }
                
                long startOpen = System.currentTimeMillis();
                main.open();
                main.startRetrieve = System.currentTimeMillis();
                List<String> argList = cl.getArgList();
                if (argList.isEmpty())
                    main.retrieve();
                else
                    for (String arg : argList)
                        main.retrieve(new File(arg));
                
       if (main.logPath != null) {
           main.logBuilder = new StringBuilder();
           DateFormat timeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS");
           main.logBuilder.append("\n");
           main.logBuilder.append(timeFormat.format(new Date()));
           main.logBuilder.append(" *INFO*");
           main.logBuilder.append(" MoveSCU:dicom");
           main.logBuilder.append(" association:");
           main.logBuilder.append(String.valueOf((main.startRetrieve - startOpen)));
           
           StringBuilder buf = new StringBuilder(" config:");

           for (int i = 0; i < args.length; i++) {
               if (args[i].contains("@")) {
                   buf.append(args[i]);
                   break;
               }
           }
           buf.append(" ref:");
           for (int i = 0; i < args.length; i++) {
               if (args[i].contains("-m")) {
                   if (i + 1 < args.length)
                       buf.append(args[i + 1]);
               }
           }
           main.endLogLine = buf.toString();
       }
            } finally {
                main.close();
                executorService.shutdown();
                scheduledExecutorService.shutdown();
            }
       } catch (ParseException e) {
            System.err.println("movescu: " + e.getMessage());
            System.err.println(rb.getString("try"));
            System.exit(2);
        } catch (Exception e) {
            System.err.println("movescu: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }


    private void logEndOfRetrieve() {

        if (logPath != null) {
            long endRetrieve = System.currentTimeMillis();
            File file = new File(logPath);
            Writer writer = null;
            try {
                File[] files = storageDir.listFiles();
                logBuilder.append(" nbFiles:");
                logBuilder.append(files.length);
                logBuilder.append(" size:");
                long size = 0;
                for (File f : files) {
                    size += f.length();
                }
                logBuilder.append(size);
                logBuilder.append(" time:");
                logBuilder.append(String.valueOf(endRetrieve - startRetrieve));
                logBuilder.append(" rate:");
                // rate in kB/s or B/ms
                DecimalFormat format = new DecimalFormat("#.##");
                logBuilder.append(format.format(new Long(size).doubleValue() / (endRetrieve - startRetrieve)));
                logBuilder.append(endLogLine);
                writer = new BufferedWriter(new FileWriter(file, true));
                writer.write(logBuilder.toString());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    writer.close();
                } catch (Exception ex) {
                }
                CLIUtils.recursiveDelete(storageDir, false, LOG);
                System.exit(0);
            }
        }
    }
   
    
    private static void configureServiceClass(MoveSCU main, CommandLine cl) throws ParseException {
        main.setInformationModel(informationModelOf(cl),
                CLIUtils.transferSyntaxesOf(cl), cl.hasOption("relational"));
    }

    private static String destinationOf(CommandLine cl) throws ParseException {
        if (cl.hasOption("dest"))
            return cl.getOptionValue("dest");
        throw new ParseException(rb.getString("missing-dest"));
    }

    private static void configureKeys(MoveSCU main, CommandLine cl) {
        if (cl.hasOption("m")) {
            String[] keys = cl.getOptionValues("m");
            for (int i = 1; i < keys.length; i++, i++)
                main.addKey(CLIUtils.toTag(keys[i - 1]), StringUtils.split(keys[i], '/'));
        }
        if (cl.hasOption("L"))
            main.addLevel(cl.getOptionValue("L"));
        if (cl.hasOption("i"))
            main.setInputFilter(CLIUtils.toTags(cl.getOptionValues("i")));
    }

    private static InformationModel informationModelOf(CommandLine cl) throws ParseException {
        try {
            return cl.hasOption("M")
                    ? InformationModel.valueOf(cl.getOptionValue("M"))
                    : InformationModel.StudyRoot;
        } catch(IllegalArgumentException e) {
            throw new ParseException(MessageFormat.format(
                    rb.getString("invalid-model-name"),
                    cl.getOptionValue("M")));
        }
    }

    public void open() throws IOException, InterruptedException,
            IncompatibleConnectionException, GeneralSecurityException {
        as = ae.connect(conn, remote, rq);
    }

    public void close() throws IOException, InterruptedException {
        if (as != null && as.isReadyForDataTransfer()) {
            as.waitForOutstandingRSP();
            as.release();
        }
    }

    public void retrieve(File f) throws IOException, InterruptedException {
        Attributes attrs = new Attributes();
        DicomInputStream dis = null;
        try {
            attrs.addSelected(new DicomInputStream(f).readDataset(-1, -1), inFilter);
        } finally {
            SafeClose.close(dis);
        }
        attrs.addAll(keys);
        retrieve(attrs);
    }

    public void retrieve() throws IOException, InterruptedException {
        retrieve(keys);
    }

    private void retrieve(Attributes keys) throws IOException, InterruptedException {
         DimseRSPHandler rspHandler = new DimseRSPHandler(as.nextMessageID()) {

            @Override
            public void onDimseRSP(Association as, Attributes cmd,
                    Attributes data) {
                super.onDimseRSP(as, cmd, data);
				if(cmd != null){
					int rop = cmd.getInt(Tag.NumberOfRemainingSuboperations, 0);
					if (rop == 0) {
						logEndOfRetrieve();
					}
				}
            }
        };

        as.cmove(model.cuid, priority, keys, null, destination, rspHandler);
    }

}
