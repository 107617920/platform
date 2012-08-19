package org.labkey.study.reports;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.study.StudyService;
import java.util.Map;

/**
 * User: adam
 * Date: 8/16/12
 * Time: 6:41 AM
 */
public class ParticipantReportDescriptor extends ReportDescriptor
{
    static final String TYPE = "participantReportDescriptor";

    public ParticipantReportDescriptor()
    {
        setDescriptorType(TYPE);
    }

    @Override
    protected String adjustPropertyValue(@Nullable ImportContext context, String key, Object value)
    {
        if (null != context && context.isAlternateIds() && "groups".equals(key))
        {
            Map<String, String> alternateIdMap = StudyService.get().getAlternateIdMap(context.getContainer());   // Translate the IDs
            JSONArray json = new JSONArray((String) value);
            JSONArray transformedJson = new JSONArray();

            for (int i = 0; i < json.length(); i++)
            {
                JSONObject item = json.getJSONObject(i);
                if (item.get("type").equals("participant"))
                {
                    String newId = alternateIdMap.get(item.get("id"));
                    item.put("id", newId);
                    item.put("label", newId);
                    transformedJson.put(item);
                }
            }

            value = transformedJson.toString();
        }

        return super.adjustPropertyValue(context, key, value);
    }
}
