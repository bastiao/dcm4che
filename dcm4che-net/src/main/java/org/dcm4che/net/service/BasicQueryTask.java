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

package org.dcm4che.net.service;

import java.io.IOException;
import java.util.NoSuchElementException;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.net.Association;
import org.dcm4che.net.Commands;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.PresentationContext;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class BasicQueryTask implements QueryTask {

    protected final Association as;
    protected final PresentationContext pc;
    protected final Attributes rq;
    protected final Attributes keys;
    protected volatile boolean canceled;

    public BasicQueryTask(Association as, PresentationContext pc, Attributes rq, Attributes keys) {
        this.as = as;
        this.pc = pc;
        this.rq = rq;
        this.keys = keys;
    }

    @Override
    public void onCancelRQ(Association as) {
        canceled = true;
    }

    @Override
    public void run() {
        try {
            try {
                while (!canceled && hasMoreMatches()) {
                    Attributes match = adjust(nextMatch(), keys, as);
                    int status = optionalKeyNotSupported(match, keys) ? Status.PendingWarning
                            : Status.Pending;
                    as.writeDimseRSP(pc, Commands.mkRSP(rq, status), match);
                }
                int status = hasMoreMatches() ? Status.Cancel : Status.Success;
                as.writeDimseRSP(pc, Commands.mkRSP(rq, status));
            } catch (DicomServiceException e) {
                as.writeDimseRSP(pc, e.getCommand(), e.getDataset());
            } finally {
                close();
            }
        } catch (IOException e) {
            // handled by Association
        }
    }

    protected void close() {
    }

    protected Attributes nextMatch() throws DicomServiceException {
        throw new NoSuchElementException();
    }

    protected boolean hasMoreMatches() throws DicomServiceException {
        return false;
    }

    protected Attributes adjust(Attributes match, Attributes keys, Association as) {
        Attributes filtered = new Attributes(match.size());
        // include SpecificCharacterSet also if not in keys
        if (!keys.contains(Tag.SpecificCharacterSet)) {
            String[] ss = match.getStrings(Tag.SpecificCharacterSet);
            if (ss != null)
                filtered.setString(Tag.SpecificCharacterSet, VR.CS, ss);
        }
        filtered.addSelected(match, keys);
        return filtered;
    }

    protected boolean optionalKeyNotSupported(Attributes match, Attributes keys) {
        Attributes notSupported = new Attributes(keys.size());
        notSupported.addNotSelected(keys, match);
        notSupported.remove(Tag.SpecificCharacterSet);
        return !notSupported.isEmpty();
    }

    protected DicomServiceException wrapException(int status, Throwable e) {
        return new DicomServiceException(rq, status, e);
    }
}