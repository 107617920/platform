package org.labkey.study.importer;

import org.labkey.api.admin.ImportException;
import org.labkey.api.data.DbScope;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.writer.StudyPropertiesWriter;
import org.labkey.study.xml.ExportDirType;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 3/17/14.
 */
public class StudyPropertiesImporter extends DefaultStudyDesignImporter
{
    Map<Object, Object> _personnelIdMap = new HashMap<>();
    private SharedTableMapBuilder _personnelTableMapBuilder = new SharedTableMapBuilder(_personnelIdMap, "Label");

    /**
     * Exports additional study related properties into the properties sub folder
     */
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        ExportDirType dirType = ctx.getXml().getProperties();

        if (dirType != null)
        {
            VirtualFile vf = root.getDir(dirType.getDir());
            if (vf != null)
            {
                DbScope scope = StudySchema.getInstance().getSchema().getScope();
                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    // import any custom study design table properties
                    importTableinfo(ctx, vf, StudyPropertiesWriter.SCHEMA_FILENAME);

                    // import the objectve and personnel tables
                    StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);
                    StudyQuerySchema projectSchema = ctx.isDataspaceProject() ? new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getProject()), ctx.getUser(), true) : schema;
                    List<String> studyPropertyTableNames = new ArrayList<>();

                    studyPropertyTableNames.add(StudyQuerySchema.OBJECTIVE_TABLE_NAME);
                    studyPropertyTableNames.add(StudyQuerySchema.PROPERTIES_TABLE_NAME);

                    for (String tableName : studyPropertyTableNames)
                    {
                        StudyQuerySchema.TablePackage tablePackage = schema.getTablePackage(ctx, projectSchema, tableName);
                        importTableData(ctx, vf, tablePackage, null, null);
                    }

                    StudyQuerySchema.TablePackage personnelTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.PERSONNEL_TABLE_NAME);
                    importTableData(ctx, vf, personnelTablePackage, _personnelTableMapBuilder,
                            ctx.isDataspaceProject() ? new PreserveExistingProjectData(ctx.getUser(), personnelTablePackage.getTableInfo(), "Label", "RowId", _personnelIdMap) : null);

                    transaction.commit();
                }

                if (ctx.isDataspaceProject())
                    ctx.addDataspaceTableIdMap("Personnel", _personnelIdMap);
            }
            else
                throw new ImportException("Unable to open the folder at : " + dirType.getDir());
        }
    }
}
