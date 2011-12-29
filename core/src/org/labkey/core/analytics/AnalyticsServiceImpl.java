/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.core.analytics;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.analytics.AnalyticsService;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.Map;

public class AnalyticsServiceImpl implements AnalyticsService.Interface
{
    static public AnalyticsServiceImpl get()
    {
        return (AnalyticsServiceImpl) AnalyticsService.get();
    }

    static public void register()
    {
        AnalyticsService.set(new AnalyticsServiceImpl());
    }


    private static final String PROP_CATEGORY = "analytics";
    public static final String DEFAULT_ACCOUNT_ID = "UA-3989586-1";

    public enum TrackingStatus
    {
        disabled,
        enabled
    }

    public enum AnalyticsProperty
    {
        trackingStatus,
        accountId,
    }

    private String getProperty(AnalyticsProperty property)
    {
        Map<String, String> properties = PropertyManager.getProperties(PROP_CATEGORY);
        return properties.get(property.toString());
    }

    public void setSettings(TrackingStatus trackingStatus, String accountId)
    {
        PropertyManager.PropertyMap properties = PropertyManager.getWritableProperties(PROP_CATEGORY, true);
        properties.put(AnalyticsProperty.trackingStatus.toString(), trackingStatus.toString());
        properties.put(AnalyticsProperty.accountId.toString(), accountId);
        PropertyManager.saveProperties(properties);
    }

    public TrackingStatus getTrackingStatus()
    {
        String strStatus = getProperty(AnalyticsProperty.trackingStatus);
        if (strStatus == null)
        {
            return TrackingStatus.disabled;
        }
        try
        {
            return TrackingStatus.valueOf(strStatus);
        }
        catch (IllegalArgumentException iae)
        {
            return TrackingStatus.disabled;
        }
    }

    public String getAccountId()
    {
        String accountId = getProperty(AnalyticsProperty.accountId);
        if (accountId != null)
        {
            return accountId;
        }
        return DEFAULT_ACCOUNT_ID;
    }

    private boolean showTrackingScript(ViewContext context)
    {
        switch (getTrackingStatus())
        {
            default:
                return false;
            case enabled:
                return true;
        }
    }

    /**
     * Returns the page url that we will report to Analytics.
     * For privacy reasons, we strip off the URL parameters if the container does not allow guest access.
     * We append the serverGUID parameter to the URL.
     */
    public String getSanitizedUrl(ViewContext context)
    {
        ActionURL actionUrl = context.cloneActionURL();
        Container container = context.getContainer();
        if (!container.hasPermission(UserManager.getGuestUser(), ReadPermission.class))
        {
            actionUrl.deleteParameters();
            actionUrl.setExtraPath(container.getId());
        }
        // Add the server GUID to the URL.  Remove the "-" because they are problematic for Google Analytics regular
        // expressions.
        String guid = AppProps.getInstance().getServerGUID();
        guid = StringUtils.replace(guid, "-", "");
        actionUrl.addParameter("serverGUID", guid);
        return actionUrl.toString();
    }

    static final private String TRACKING_SCRIPT_INTRO = "<script type=\"text/javascript\">\n" +
            "var gaJsHost = ((\"https:\" == document.location.protocol) ? \"https://ssl.\" : \"http://www.\");\n" +
            "document.write(unescape(\"%3Cscript src='\" + gaJsHost + \"google-analytics.com/ga.js' type='text/javascript'%3E%3C/script%3E\"));\n" +
            "</script>\n";

    /**
     * The Google Analytics tracking script.
     * <p>For an explanation of what settings are available on the pageTracker object, see
     * <a href="http://code.google.com/apis/analytics/docs/gaJSApi.html">Google Analytics Tracking API</a>
     */
    static final private String TRACKING_SCRIPT_TEMPLATE
            = TRACKING_SCRIPT_INTRO +
            "<script type=\"text/javascript\">\n" +
            "var pageTracker = _gat._getTracker(|ACCOUNT_ID|);\n" +
            "pageTracker._initData();\n" +
            "pageTracker._setDetectTitle(false);\n" +
            "pageTracker._trackPageview(|PAGE_URL|);\n" +
            "</script>";

    public String getTrackingScript(ViewContext context)
    {
        if (!showTrackingScript(context))
        {
            return "";
        }
        String trackingScript = TRACKING_SCRIPT_TEMPLATE;
        trackingScript = StringUtils.replace(trackingScript, "|ACCOUNT_ID|", PageFlowUtil.jsString(getAccountId()));
        trackingScript = StringUtils.replace(trackingScript, "|PAGE_URL|", PageFlowUtil.jsString(getSanitizedUrl(context)));
        return trackingScript;
    }
}
