/*
 * Copyright (c) 2009-2012 LabKey Corporation
 *
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
 */
package org.labkey.study.writer;

import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.study.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.CopyOnWriteArrayList;

public class StudySerializationRegistryImpl implements StudySerializationRegistry
{
    private static final StudySerializationRegistryImpl INSTANCE = new StudySerializationRegistryImpl();
    private static final Collection<FolderWriterFactory> WRITER_FACTORIES = new CopyOnWriteArrayList<FolderWriterFactory>();
    private static final Collection<FolderImporterFactory> IMPORTER_FACTORIES = new CopyOnWriteArrayList<FolderImporterFactory>();

    private StudySerializationRegistryImpl()
    {
    }

    public static StudySerializationRegistryImpl get()
    {
        return INSTANCE;
    }

    // These writers are defined and registered by other modules.  They have no knowledge of study internals, other
    // than being able to write elements into study.xml.
    public Collection<FolderWriter> getRegisteredStudyWriters()
    {
        // New up the writers every time since these classes can be stateful
        Collection<FolderWriter> writers = new LinkedList<FolderWriter>();

        for (FolderWriterFactory factory : WRITER_FACTORIES)
            writers.add(factory.create());

        return writers;
    }

    // These importers are defined and registered by other modules.  They have no knowledge of study internals, other
    // than being able to read elements from study.xml.
    public Collection<FolderImporter> getRegisteredStudyImporters()
    {
        // New up the writers every time since these classes can be stateful
        Collection<FolderImporter> importers = new LinkedList<FolderImporter>();

        for (FolderImporterFactory factory : IMPORTER_FACTORIES)
            importers.add(factory.create());

        return importers;
    }

    public void addFactories(FolderWriterFactory writerFactory, FolderImporterFactory importerFactory)
    {
        WRITER_FACTORIES.add(writerFactory);
        IMPORTER_FACTORIES.add(importerFactory);
    }

    public void addImportFactory(FolderImporterFactory importerFactory)
    {
        IMPORTER_FACTORIES.add(importerFactory);
    }

    // These writers are internal to study.  They have access to study internals.
    public Collection<InternalStudyWriter> getInternalStudyWriters()
    {
        // New up the writers every time since these classes can be stateful
        return Arrays.asList(
            new VisitMapWriter(),
            new CohortWriter(),
            new QcStateWriter(),
            new DatasetWriter(),
            new AssayDatasetWriter(),
            new SpecimenArchiveWriter(),
            new ParticipantCommentWriter(),
            new ParticipantGroupWriter(),
            new ProtocolDocumentWriter(),
            new ViewCategoryWriter(),
            new StudyXmlWriter()  // Note: Must be the last study writer since it writes out the study.xml file (to which other writers contribute)
        );
    }
}