/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.study.controllers.samples;

import org.labkey.api.security.*;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.action.*;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.HttpView;
import org.labkey.api.data.Container;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.SampleManager;
import org.labkey.study.security.permissions.RequestSpecimensPermission;
import org.labkey.study.security.permissions.ManageRequestsPermission;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.model.*;
import org.springframework.validation.BindException;

import java.util.*;
import java.sql.SQLException;
/*
 * User: brittp
 * Date: Dec 18, 2008
 * Time: 11:57:24 AM
 */

public class SpecimenApiController extends BaseStudyController
{
    private static final DefaultActionResolver _resolver = new DefaultActionResolver(SpecimenApiController.class);

    public SpecimenApiController()
    {
        super();
        setActionResolver(_resolver);
    }

    public static class SampleApiForm implements HasViewContext
    {
        private ViewContext _viewContext;

        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        public void setViewContext(ViewContext viewContext)
        {
            _viewContext = viewContext;
        }
    }

    public static class GetRequestsForm extends SampleApiForm
    {
        Boolean _allUsers;

        public Boolean isAllUsers()
        {
            return _allUsers;
        }

        public void setAllUsers(Boolean allUsers)
        {
            _allUsers = allUsers;
        }
    }

    public static class RequestIdForm extends SampleApiForm
    {
        private int _requestId;

        public int getRequestId()
        {
            return _requestId;
        }

        public void setRequestId(int requestId)
        {
            _requestId = requestId;
        }
    }

    private List<Map<String, Object>> getSpecimenListResponse(Specimen[] vials) throws SQLException
    {
        List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
        for (Specimen vial : vials)
        {
            Map<String, Object> vialProperties = new HashMap<String, Object>();
            response.add(vialProperties);
            vialProperties.put("rowId", vial.getRowId());
            vialProperties.put("globalUniqueId", vial.getGlobalUniqueId());
            vialProperties.put("ptid", vial.getPtid());
            vialProperties.put("visitValue", vial.getVisitValue());
            vialProperties.put("primaryTypeId", vial.getPrimaryTypeId());
            if (vial.getPrimaryTypeId() != null)
            {
                PrimaryType primaryType = SampleManager.getInstance().getPrimaryType(vial.getContainer(), vial.getPrimaryTypeId().intValue());
                if (primaryType != null)
                    vialProperties.put("primaryType", primaryType.getPrimaryType());
            }
            vialProperties.put("derivativeTypeId", vial.getDerivativeTypeId());
            if (vial.getDerivativeTypeId() != null)
            {
                DerivativeType derivativeType = SampleManager.getInstance().getDerivativeType(vial.getContainer(), vial.getDerivativeTypeId().intValue());
                if (derivativeType != null)
                    vialProperties.put("derivativeType", derivativeType.getDerivative());
            }
            vialProperties.put("additiveTypeId", vial.getAdditiveTypeId());
            if (vial.getAdditiveTypeId() != null)
            {
                AdditiveType additiveType = SampleManager.getInstance().getAdditiveType(vial.getContainer(), vial.getAdditiveTypeId().intValue());
                if (additiveType != null)
                    vialProperties.put("additiveType", additiveType.getAdditive());
            }
            vialProperties.put("currentLocation", vial.getCurrentLocation() != null ?
                    getLocation(getContainer(), vial.getCurrentLocation().intValue()) : null);
            if (vial.getOriginatingLocationId() != null)
                vialProperties.put("originatingLocation", getLocation(getContainer(), vial.getOriginatingLocationId().intValue()));
            vialProperties.put("subAdditiveDerivative", vial.getSubAdditiveDerivative());
            vialProperties.put("volume", vial.getVolume());
            vialProperties.put("specimenHash", vial.getSpecimenHash());
            vialProperties.put("volumeUnits", vial.getVolumeUnits());
        }
        return response;
    }

    private Map<String, Object> getRequestResponse(ViewContext context, SampleRequest request) throws SQLException
    {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("requestId", request.getRowId());
        map.put("comments", request.getComments());
        map.put("created", request.getCreated());
        map.put("createdBy", request.getCreatedBy());
        User user = UserManager.getUser(request.getCreatedBy());
        Map<String, Object> userMap = new HashMap<String, Object>();
        userMap.put("userId", request.getCreatedBy());
        userMap.put("displayName", user.getDisplayNameOld(context));
        map.put("createdBy", userMap);
        map.put("destination", request.getDestinationSiteId() != null ? getLocation(getContainer(), request.getDestinationSiteId().intValue()) : null);
        map.put("statusId", request.getStatusId());
        SampleRequestStatus status = SampleManager.getInstance().getRequestStatus(request.getContainer(), request.getStatusId());
        if (status != null)
            map.put("status", status.getLabel());
        Specimen[] vials = SampleManager.getInstance().getRequestSpecimens(request);
        map.put("vials", getSpecimenListResponse(vials));
        return map;
    }

    private Map<String, Object> getLocation(Container container, int siteId) throws SQLException
    {
        SiteImpl location = StudyManager.getInstance().getSite(container, siteId);
        if (location == null)
            return null;
        return getLocation(location);
    }

    private Map<String, Object> getLocation(SiteImpl site)
    {
        Map<String, Object> location = new HashMap<String, Object>();
        location.put("endpoint", site.isEndpoint());
        location.put("entityId", site.getEntityId());
        location.put("label", site.getLabel());
        location.put("labUploadCode", site.getLabUploadCode());
        location.put("labwareLabCode", site.getLabwareLabCode());
        location.put("ldmsLabCode", site.getLdmsLabCode());
        location.put("repository", site.isRepository());
        location.put("rowId", site.getRowId());
        location.put("SAL", site.isSal());
        location.put("clinic", site.isClinic());
        location.put("externalId", site.getExternalId());
        return location;
    }

    private List<Map<String, Object>> getRequestListResponse(ViewContext context, List<SampleRequest> requests) throws SQLException
    {
        List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
        for (SampleRequest request : requests)
            response.add(getRequestResponse(context, request));
        return response;
    }

    @RequiresPermissionClass(ReadPermission.class)
    @ApiVersion(9.1)
    public class GetRepositoriesAction extends ApiAction<SampleApiForm>
    {
        public ApiResponse execute(SampleApiForm form, BindException errors) throws Exception
        {
            final List<Map<String, Object>> repositories = new ArrayList<Map<String, Object>>();
            for (SiteImpl site : StudyManager.getInstance().getSites(getContainer()))
            {
                if (site.isRepository())
                    repositories.add(getLocation(site));
            }
            return new ApiResponse()
            {
                public Map<String, Object> getProperties()
                {
                    Map<String, Object> result = new HashMap<String, Object>();
                    result.put("repositories", repositories);
                    return result;
                }
            };
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @ApiVersion(9.1)
    public class GetOpenRequestsAction extends ApiAction<GetRequestsForm>
    {
        public ApiResponse execute(GetRequestsForm requestsForm, BindException errors) throws Exception
        {
            Container container = requestsForm.getViewContext().getContainer();
            User user = requestsForm.getViewContext().getUser();
            SampleRequestStatus shoppingCartStatus = SampleManager.getInstance().getRequestShoppingCartStatus(container, user);
            final Map<String, Object> response = new HashMap<String, Object>();
            if (user != null && shoppingCartStatus != null)
            {
                boolean allUsers = getContainer().hasPermission(getUser(), ManageRequestsPermission.class);
                if (requestsForm.isAllUsers() != null)
                    allUsers = requestsForm.isAllUsers().booleanValue();
                SampleRequest[] allUserRequests = SampleManager.getInstance().getRequests(container, allUsers ? null : user);
                List<SampleRequest> nonFinalRequests = new ArrayList<SampleRequest>();
                for (SampleRequest request : allUserRequests)
                {
                    if (request.getStatusId() == shoppingCartStatus.getRowId())
                        nonFinalRequests.add(request);
                }
                response.put("requests", getRequestListResponse(getViewContext(), nonFinalRequests));
            }
            else
                response.put("requests", Collections.emptyList());

            return new ApiResponse()
            {
                public Map<String, Object> getProperties()
                {
                    return response;
                }
            };
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @ApiVersion(9.1)
    public class GetRequestAction extends ApiAction<RequestIdForm>
    {
        public ApiResponse execute(RequestIdForm requestIdForm, BindException errors) throws Exception
        {
            SampleRequest request = getRequest(getUser(), getContainer(), requestIdForm.getRequestId(), false, false);
            final Map<String, Object> response = new HashMap<String, Object>();
            response.put("request", request != null ? getRequestResponse(getViewContext(), request) : null);
            return new ApiResponse()
            {
                public Map<String, Object> getProperties()
                {
                    return response;
                }
            };
        }
    }

    public static class GetVialsByRowIdForm extends SampleApiForm
    {
        int[] _rowIds;

        public int[] getRowIds()
        {
            return _rowIds;
        }

        public void setRowIds(int[] rowIds)
        {
            _rowIds = rowIds;
        }
    }

    public static class GetProvidingLocationsForm extends RequestIdForm
    {
        private String[] specimenHashes;

        public String[] getSpecimenHashes()
        {
            return specimenHashes;
        }

        public void setSpecimenHashes(String[] specimenHashes)
        {
            this.specimenHashes = specimenHashes;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @ApiVersion(9.1)
    public class GetProvidingLocations extends ApiAction<GetProvidingLocationsForm>
    {
        public ApiResponse execute(GetProvidingLocationsForm form, BindException errors) throws Exception
        {
            Map<String, List<Specimen>> vialsByHash = SampleManager.getInstance().getVialsForSampleHashes(getContainer(),
                    PageFlowUtil.set(form.getSpecimenHashes()), true);
            Collection<Integer> preferredLocations = SpecimenUtils.getPreferredProvidingLocations(vialsByHash.values());
            final Map<String, Object> response = new HashMap<String, Object>();
            List<Map<String, Object>> locations = new ArrayList<Map<String, Object>>();
            for (Integer locationId : preferredLocations)
                locations.add(getLocation(getContainer(), locationId));
            response.put("locations", locations);
            return new ApiResponse()
            {
                public Map<String, Object> getProperties()
                {
                    return response;
                }
            };
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @ApiVersion(9.1)
    public class GetVialsByRowIdAction extends ApiAction<GetVialsByRowIdForm>
    {
        public ApiResponse execute(GetVialsByRowIdForm form, BindException errors) throws Exception
        {
            Container container = form.getViewContext().getContainer();
            final Map<String, Object> response = new HashMap<String, Object>();
            List<Map<String, Object>> vialList;
            if (form.getRowIds() != null && form.getRowIds().length > 0)
            {
                Specimen[] vials = SampleManager.getInstance().getSpecimens(container, form.getRowIds());
                vialList = getSpecimenListResponse(vials);
            }
            else
                vialList = Collections.emptyList();
            response.put("vials", vialList);
            return new ApiResponse()
            {
                public Map<String, Object> getProperties()
                {
                    return response;
                }
            };
        }
    }

    public static class VialRequestForm extends RequestIdForm
    {
        public static enum IdTypes
        {
            GlobalUniqueId,
            SpecimenHash,
            RowId
        }

        private String _idType;
        private String[] _vialIds;

        public String[] getVialIds()
        {
            return _vialIds;
        }

        public void setVialIds(String[] vialIds)
        {
            _vialIds = vialIds;
        }

        public String getIdType()
        {
            return _idType;
        }

        public void setIdType(String idType)
        {
            _idType = idType;
        }
    }

    public static class AddSampleToRequestForm extends RequestIdForm
    {
        private String[] specimenHashes;
        private Integer _preferredLocation;

        public String[] getSpecimenHashes()
        {
            return specimenHashes;
        }

        public void setSpecimenHashes(String[] specimenHashes)
        {
            this.specimenHashes = specimenHashes;
        }

        public Integer getPreferredLocation()
        {
            return _preferredLocation;
        }

        public void setPreferredLocation(Integer preferredLocation)
        {
            _preferredLocation = preferredLocation;
        }
    }

    private SampleRequest getRequest(User user, Container container, int rowId, boolean checkOwnership, boolean checkEditability) throws SQLException
    {
        SampleRequest request = SampleManager.getInstance().getRequest(container, rowId);
        boolean admin = container.hasPermission(user, RequestSpecimensPermission.class);
        boolean adminOrOwner = request != null && (admin || request.getCreatedBy() == user.getUserId());
        if (request == null || (checkOwnership && !adminOrOwner))
            throw new RuntimeException("Request " + rowId + " was not found or the current user does not have permissions to access it.");
        if (checkEditability)
        {
            if (admin)
            {
                if (SampleManager.getInstance().isInFinalState(request))
                    throw new RuntimeException("Request " + rowId + " is in a final state and cannot be modified.");
            }
            else
            {
                SampleRequestStatus cartStatus = SampleManager.getInstance().getRequestShoppingCartStatus(container, user);
                if (cartStatus == null || request.getStatusId() != cartStatus.getRowId())
                    throw new RuntimeException("Request " + rowId + " has been submitted and can only be modified by an administrator.");
            }
        }
        return request;
    }

    @RequiresPermissionClass(RequestSpecimensPermission.class)
    @ApiVersion(9.1)
    public class AddVialsToRequestAction extends ApiAction<VialRequestForm>
    {
        public ApiResponse execute(VialRequestForm vialRequestForm, BindException errors) throws Exception
        {
            SampleRequest request = getRequest(getUser(), getContainer(), vialRequestForm.getRequestId(), true, true);
            for (String vialId : vialRequestForm.getVialIds())
            {
                Specimen vial = getVial(vialId, vialRequestForm.getIdType());
                SampleManager.getInstance().createRequestSampleMapping(getUser(), request, Collections.singletonList(vial), true, true);
            }
            final Map<String, Object> response = getRequestResponse(getViewContext(), request);
            return new ApiResponse()
            {
                public Map<String, Object> getProperties()
                {
                    return response;
                }
            };
        }
    }

    private Specimen getVial(String vialId, String idType) throws SQLException
    {
        Specimen vial;
        if (VialRequestForm.IdTypes.GlobalUniqueId.name().equals(idType))
            vial = SampleManager.getInstance().getSpecimen(getContainer(), vialId);
        else if (VialRequestForm.IdTypes.RowId.name().equals(idType))
        {
            try
            {
                int id = Integer.parseInt(vialId);
                vial = SampleManager.getInstance().getSpecimen(getContainer(), id);
            }
            catch (NumberFormatException e)
            {
                throw new RuntimeException(vialId + " could not be converted into a valid integer RowId.");
            }
        }
        else
        {
            throw new RuntimeException("Invalid ID type \"" + idType + "\": only \"" +
                    VialRequestForm.IdTypes.GlobalUniqueId.name() + "\" and \"" + VialRequestForm.IdTypes.RowId.name() +
                    "\" are valid parameter values.");
        }
        if (vial == null)
        {
            HttpView.throwNotFound("No vial was found with " +  idType + " " + vialId + ".");
            return null;
        }
        return vial;
    }

    @RequiresPermissionClass(RequestSpecimensPermission.class)
    @ApiVersion(9.1)
    public class RemoveVialsFromRequestAction extends ApiAction<VialRequestForm>
    {
        public ApiResponse execute(VialRequestForm vialRequestForm, BindException errors) throws Exception
        {
            SampleRequest request = getRequest(getUser(), getContainer(), vialRequestForm.getRequestId(), true, true);
            List<Integer> rowIds = new ArrayList<Integer>();
            Specimen[] currentSpecimens = request.getSpecimens();
            for (String vialId : vialRequestForm.getVialIds())
            {
                Specimen vial = getVial(vialId, vialRequestForm.getIdType());
                Specimen toRemove = null;
                for (int i = 0; i < currentSpecimens.length && toRemove == null; i++)
                {
                    Specimen possible = currentSpecimens[i];
                    if (possible.getRowId() == vial.getRowId())
                        toRemove = possible;
                }
                if (toRemove != null)
                    rowIds.add(toRemove.getRowId());
            }
            if (!rowIds.isEmpty())
            {
                int[] rowIdArray = new int[rowIds.size()];
                for (int i = 0; i < rowIdArray.length; i++)
                    rowIdArray[i] = rowIds.get(i).intValue();
                SampleManager.getInstance().deleteRequestSampleMappings(getUser(), request, rowIdArray, true);
            }
            final Map<String, Object> response = getRequestResponse(getViewContext(), request);
            return new ApiResponse()
            {
                public Map<String, Object> getProperties()
                {
                    return response;
                }
            };
        }
    }

    @RequiresPermissionClass(RequestSpecimensPermission.class)
    @ApiVersion(9.1)
    public class AddSamplesToRequestAction extends ApiAction<AddSampleToRequestForm>
    {
        public ApiResponse execute(AddSampleToRequestForm addSampleToRequestForm, BindException errors) throws Exception
        {
            final SampleRequest request = getRequest(getUser(), getContainer(), addSampleToRequestForm.getRequestId(), true, true);
            Set<String> hashes = new HashSet<String>();
            Collections.addAll(hashes, addSampleToRequestForm.getSpecimenHashes());
            SpecimenUtils.RequestedSpecimens requested = getUtils().getRequestableBySampleHash(hashes, addSampleToRequestForm.getPreferredLocation());
            if (requested.getSpecimens().length > 0)
            {
                List<Specimen> specimens = new ArrayList<Specimen>(requested.getSpecimens().length);
                Collections.addAll(specimens, requested.getSpecimens());
                SampleManager.getInstance().createRequestSampleMapping(getUser(), request, specimens, true, true);
            }
            final Map<String, Object> response = getRequestResponse(getViewContext(), request);
            return new ApiResponse()
            {
                public Map<String, Object> getProperties()
                {
                    return response;
                }
            };
        }
    }

    @RequiresPermissionClass(RequestSpecimensPermission.class)
    @ApiVersion(9.1)
    public class CancelRequestAction extends ApiAction<RequestIdForm>
    {
        public ApiResponse execute(RequestIdForm deleteRequestForm, BindException errors) throws Exception
        {
            SampleRequest request = getRequest(getUser(), getContainer(), deleteRequestForm.getRequestId(), true, true);
            SampleManager.getInstance().deleteRequest(getUser(), request);
            return new ApiResponse()
            {
                public Map<String, Object> getProperties()
                {
                    return null;
                }
            };
        }
    }

}