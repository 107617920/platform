package org.labkey.study.importer;

import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.MvUtil;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.xml.MissingValueIndicatorsType;
import org.labkey.study.xml.StudyDocument;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * User: cnathe
 * Date: May 1, 2012
 */
public class MissingValueImporterFactory implements FolderImporterFactory
{
    @Override
    public FolderImporter create()
    {
        return new MissingValueImporter();
    }

    @Override
    public boolean isFinalImporter()
    {
        return false;
    }

    public class MissingValueImporter implements FolderImporter
    {
        @Override
        public String getDescription()
        {
            return "missing value indicators";
        }

        @Override
        public void process(@Nullable PipelineJob job, ImportContext ctx, VirtualFile root) throws Exception
        {
            MissingValueIndicatorsType mvXml = getMissingValueIndicatorsFromXml(ctx.getXml());

            if (null != mvXml)
            {
                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());
                MissingValueIndicatorsType.MissingValueIndicator[] mvs = mvXml.getMissingValueIndicatorArray();

                // Create a map that looks just like the map returned by MvUtil.getIndicatorsAndLabels()
                Map<String, String> newMvMap = new HashMap<String, String>(mvs.length);

                for (MissingValueIndicatorsType.MissingValueIndicator mv : mvs)
                    newMvMap.put(mv.getIndicator(), mv.getLabel());

                Map<String, String> oldMvMap = MvUtil.getIndicatorsAndLabels(ctx.getContainer());

                // Only save the imported missing value indicators if they don't match the current settings exactly; this makes
                // it possible to share the same MV indicators across a folder tree, without an import breaking inheritance.
                if (!newMvMap.equals(oldMvMap))
                {
                    String[] mvIndicators = newMvMap.keySet().toArray(new String[mvs.length]);
                    String[] mvLabels = newMvMap.values().toArray(new String[mvs.length]);
                    MvUtil.assignMvIndicators(ctx.getContainer(), mvIndicators, mvLabels);
                }
                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext ctx, VirtualFile root) throws Exception
        {
            return null;
        }

        @Override
        public boolean supportsVirtualFile()
        {
            return true;
        }

        private MissingValueIndicatorsType getMissingValueIndicatorsFromXml(XmlObject xml)
        {
            // This conversion of the xml object to either a Study doc or a Folder doc is to support backward
            // compatibility for importing study archives which have the MVI info in the study.xml file
            MissingValueIndicatorsType mvi = null;
            if (xml instanceof StudyDocument.Study)
                mvi = ((StudyDocument.Study)xml).getMissingValueIndicators();
            else if (xml instanceof FolderDocument.Folder)
                mvi = ((FolderDocument.Folder)xml).getMissingValueIndicators();

            return mvi;
        }
    }
}
