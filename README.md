## This fork is an experimental build to test new image codecs ##

* Embedded new codecs compiled for Windows 32/64-bit, Linux x86 32/64-bit and Mac OS X 64-bit.  
	- jpeg-basline, jpeg-extended and jpeg-lossless: IJG 6b (reader and writer partially)  
	- jpeg-ls: CharLS 1.0 (reader and writer)  
	- jpeg2000: OpegJPEG 2.1 (reader)  
* Allows to order the codecs by priority with a unique configuration for all the systems

Advantages of the native readers:
* Allows to return BufferedImage or RenderedImage (from readAsRenderedImage()).
* Do not extract an image file from DICOM as most additional decoders, read directly from stream segments.
* Handle a [unique configuration file](https://github.com/nroduit/dcm4che/blob/dcm4che-native-codec/dcm4che-imageio/src/main/resources/org/dcm4che3/imageio/codec/ImageReaderFactory.xml) for all the systems.  
	Can be overwritten by a Java property: jnlp.weasis.org.dcm4che3.imageio.codec.ImageReaderFactory="http://server/context/ImageReaderFactory.xml"

Fix issues of Sun codecs:
* Fix multi-thread issue: https://java.net/jira/browse/JAI_IMAGEIO_CORE-126
* Fix issues of the native jpeg2000 decoders: https://java.net/jira/browse/JAI_IMAGEIO_CORE-189
* Fix another artifact issue of the native jpeg2000 decoders
* No need to patch jpeg-ls anymore
* Fix color issue of jpeg-ls sun reader
* Fix convert signed data into unsigned data buffer



dcm4che-3.x DICOM Toolkit
=========================
Sources: https://github.com/dcm4che/dcm4che  
Binaries: https://sourceforge.net/projects/dcm4che/files/dcm4che3  
Issue Tracker: http://www.dcm4che.org/jira/browse/LIB  
Build Status: [![Build Status](https://travis-ci.org/dcm4che/dcm4che.svg?branch=master)](https://travis-ci.org/dcm4che/dcm4che)

This is a complete rewrite of [dcm4che-2.x](http://www.dcm4che.org/confluence/display/d2/).

One main focus was to minimize the memory footprint of the DICOM data sets.
It already provides modules to store/fetch configuration data to/from LDAP,
compliant to the DICOM Application Configuration Management Profile,
specified in [DICOM 2011, Part 15][1], Annex H.

[1]: ftp://medical.nema.org/medical/dicom/2011/11_15pu.pdf

Build
-----
After installation of [Maven 3](http://maven.apache.org):

    > mvn install

Modules
-------
- dcm4che-audit
- dcm4che-conf
  - dcm4che-conf-api
  - dcm4che-conf-api-hl7
  - dcm4che-conf-ldap
  - dcm4che-conf-ldap-audit
  - dcm4che-conf-ldap-hl7
  - dcm4che-conf-ldap-imageio
  - dcm4che-conf-prefs
  - dcm4che-conf-prefs-audit
  - dcm4che-conf-prefs-hl7
  - dcm4che-conf-prefs-imageio
- dcm4che-core
- dcm4che-emf
- dcm4che-hl7
- dcm4che-image
- dcm4che-imageio
- dcm4che-imageio-rle
- dcm4che-net
- dcm4che-net-audit
- dcm4che-net-hl7
- dcm4che-net-imageio
- dcm4che-soundex
- dcm4che-jboss-modules
- dcm4che-servlet

Utilities
---------
- [dcm2dcm][]: Transcode DICOM file according the specified Transfer Syntax
- [dcm2jpg][]: Convert DICOM image to JPEG or other image formats
- [dcm2xml][]: Convert DICOM file in XML presentation
- [dcmdir][]: Dump, create or update DICOMDIR file
- [dcmdump][]: Dump DICOM file in textual form
- [dcmqrscp][]: Simple DICOM archive
- [dcmvalidate][]: Validate DICOM object according a specified Information Object Definition
- [emf2sf][]: Convert DICOM Enhanced Multi-frame image to legacy DICOM Single-frame images
- [findscu][]: Invoke DICOM C-FIND Query Request
- [getscu][]: Invoke DICOM C-GET Retrieve Request
- [hl72xml][]: Convert HL7 v2.x message in XML presentation
- [hl7pix][]: Query HL7 v2.x PIX Manager
- [hl7rcv][]: HL7 v2.x Receiver
- [hl7snd][]: Send HL7 v2.x message
- [ianscp][]: DICOM Instance Availability Notification receiver 
- [ianscu][]: Send DICOM Instance Availability Notification
- [mkkos][]: Make DICOM Key Object Selection Document
- [modality][]: Simulates DICOM Modality
- [movescu][]: Invoke DICOM C-MOVE Retrieve request
- [mppsscp][]: DICOM Modality Performed Procedure Step Receiver
- [mppsscu][]: Send DICOM Modality Performed Procedure Step
- [stgcmtscu][]: Invoke DICOM Storage Commitment Request
- [storescp][]: DICOM Composite Object Receiver
- [storescu][]: Send DICOM Composite Objects
- [xml2dcm][]: Create/Update DICOM file from/with XML presentation
- [xml2hl7][]: Create HL7 v2.x message from XML presentation
- [xml2prefs][]: Import Java Preferences

[dcm2dcm]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-dcm2dcm/README.md
[dcm2jpg]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-dcm2jpg/README.md
[dcm2xml]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-dcm2xml/README.md
[dcmdir]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-dcmdir/README.md
[dcmdump]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-dcmdump/README.md
[dcmqrscp]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-dcmqrscp/README.md
[dcmvalidate]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-dcmvalidate/README.md
[emf2sf]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-emf2sf/README.md
[findscu]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-findscu/README.md
[getscu]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-getscu/README.md
[hl72xml]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-hl72xml/README.md
[hl7pix]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-hl7pix/README.md
[hl7rcv]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-hl7rcv/README.md
[hl7snd]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-hl7snd/README.md
[ianscp]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-ianscp/README.md
[ianscu]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-ianscu/README.md
[mkkos]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-mkkos/README.md
[modality]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-ihe/dcm4che-tool-ihe-modality/README.md
[movescu]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-movescu/README.md
[mppsscp]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-mppsscp/README.md
[mppsscu]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-mppsscu/README.md
[stgcmtscu]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-stgcmtscu/README.md
[storescp]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-storescp/README.md
[storescu]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-storescu/README.md
[xml2dcm]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-xml2dcm/README.md
[xml2hl7]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-xml2hl7/README.md
[xml2prefs]: https://github.com/dcm4che/dcm4che/blob/master/dcm4che-tool/dcm4che-tool-xml2prefs/README.md

License
-------
* [Mozilla Public License Version 1.1](http://www.mozilla.org/MPL/1.1/)

