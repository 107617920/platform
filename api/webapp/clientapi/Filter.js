/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2012 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */

 /**
 * @namespace  Filter static class to describe and create filters.
  *            <p>Additional Documentation:
  *              <ul>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=filteringData">Filter via the LabKey UI</a></li>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=tutorialActionURL">Tutorial: Basics: Building URLs and Filters</a></li>
  *              </ul>
  *           </p>
 * @property {Object} Types Types static class to describe different types of filters.
 * @property {LABKEY.Filter.FilterDefinition} Types.EQUAL Finds rows where the column value matches the given filter value. Case-sensitivity depends upon how your underlying relational database was configured.
 * @property {LABKEY.Filter.FilterDefinition} Types.DATE_EQUAL Finds rows where the date portion of a datetime column matches the filter value (ignoring the time portion).
 * @property {LABKEY.Filter.FilterDefinition} Types.DATE_NOT_EQUAL Finds rows where the date portion of a datetime column does not match the filter value (ignoring the time portion).
 * @property {LABKEY.Filter.FilterDefinition} Types.NOT_EQUAL_OR_MISSING Finds rows where the column value does not equal the filter value, or is missing (null).
 * @property {LABKEY.Filter.FilterDefinition} Types.NOT_EQUAL Finds rows where the column value does not equal the filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.MISSING Finds rows where the column value is missing (null). Note that no filter value is required with this operator.
 * @property {LABKEY.Filter.FilterDefinition} Types.NOT_MISSING Finds rows where the column value is not missing (is not null). Note that no filter value is required with this operator.
 * @property {LABKEY.Filter.FilterDefinition} Types.GREATER_THAN Finds rows where the column value is greater than the filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.LESS_THAN Finds rows where the column value is less than the filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.GREATER_THAN_OR_EQUAL Finds rows where the column value is greater than or equal to the filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.LESS_THAN_OR_EQUAL Finds rows where the column value is less than or equal to the filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.CONTAINS Finds rows where the column value contains the filter value. Note that this may result in a slow query as this cannot use indexes.
 * @property {LABKEY.Filter.FilterDefinition} Types.DOES_NOT_CONTAIN Finds rows where the column value does not contain the filter value. Note that this may result in a slow query as this cannot use indexes.
 * @property {LABKEY.Filter.FilterDefinition} Types.DOES_NOT_START_WITH Finds rows where the column value does not start with the filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.STARTS_WITH Finds rows where the column value starts with the filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.EQUALS_ONE_OF Finds rows where the column value equals one of the supplied filter values. The values should be supplied as a semi-colon-delimited list (e.g., 'a;b;c').
 * @property {LABKEY.Filter.FilterDefinition} Types.EQUALS_ONE_OF_OR_MISSING Finds rows where the column value equals one of the supplied filter values, as well as rows where the value is missing (ie. null). The values should be supplied as a semi-colon-delimited list (e.g., 'a;b;c').
 * @property {LABKEY.Filter.FilterDefinition} Types.NOT_IN Finds rows where the column value is not in any of the supplied filter values. The values should be supplied as a semi-colon-delimited list (e.g., 'a;b;c').
 * @property {LABKEY.Filter.FilterDefinition} Types.NOT_IN_OR_MISSING Finds rows where the column value is not in any of the supplied filter values, but will include rows where the value is missing (ie. null). The values should be supplied as a semi-colon-delimited list (e.g., 'a;b;c').
 * @property {LABKEY.Filter.FilterDefinition} Types.CONTAINS_ONE_OF Finds rows where the column value contains any of the supplied filter values. The values should be supplied as a semi-colon-delimited list (e.g., 'a;b;c').
 * @property {LABKEY.Filter.FilterDefinition} Types.CONTAINS_NONE_OF Finds rows where the column value does not contain any of the supplied filter values. The values should be supplied as a semi-colon-delimited list (e.g., 'a;b;c').
 *
 */
LABKEY.Filter = new function()
{
    function validateMultiple(type, value, colName)
    {
        var values = value.split(";");
        var result = '';
        var separator = '';
        for (var i = 0; i < values.length; i++)
        {
            var value = LABKEY.ext.FormHelper.validate(values[i].trim(), type, colName);
            if (value == undefined)
                return undefined;

            result = result + separator + value;
            separator = ";";
        }
        return result;
    }

    var urlMap = {};
    var oppositeMap = {
        //HAS_ANY_VALUE: null,
        eq: 'neqornull',
        dateeq : 'dateneq',
        dateneq : 'dateeq',
        neqornull : 'eq',
        neq : 'eq',
        isblank : 'isnonblank',
        isnonblank : 'isblank',
        gt : 'lte',
        dategt : 'datelte',
        lt : 'gte',
        datelt : 'dategte',
        gte : 'lt',
        dategte : 'datelt',
        lte : 'gt',
        datelte : 'dategt',
        contains : 'doesnotcontain',
        doesnotcontain : 'contains',
        doesnotstartwith : 'startswith',
        startswith : 'doesnotstartwith',
        'in' : 'notinornull',
        inornull : 'notin',
        notin : 'inornull',
        notinornull : 'in',
        containsoneof : 'containsnoneof',
        containsnoneof : 'containsoneof',
        hasmvvalue : 'nomvvalue',
        nomvvalue : 'hasmvvalue'
    };

    function createFilterType(displayText, urlSuffix, dataValueRequired, isMultiValued)
    {
        var result = {
            getDisplayText : function() { return displayText },
            getURLSuffix : function() { return urlSuffix },
            isDataValueRequired : function() { return dataValueRequired },
            isMultiValued : function() { return isMultiValued },
            getOpposite : function() {return oppositeMap[urlSuffix] ? urlMap[oppositeMap[urlSuffix]] : null},
            validate : function (value, type, colName) {
                if (!dataValueRequired)
                    return true;

                var f = filterTypes[type];
                var found = false;
                for (var i = 0; !found && i < f.length; i++)
                {
                    if (f[i].getURLSuffix() == urlSuffix)
                        found = true;
                }
                if (!found) {
                    alert("Filter type '" + displayText + "' can't be applied to " + type + " types.");
                    return undefined;
                }

                if (result == LABKEY.Filter.Types.IN)
                    return validateMultiple(type, value, colName);
                else
                    return LABKEY.ext.FormHelper.validate(type, value, colName);
            }
        };
        urlMap[urlSuffix] = result;
        return result;
    }

    function getFilter(columnName, value, filterType)
    {
        return {
            getColumnName: function() {return columnName;},
            getValue: function() {return value},
            getFilterType: function() {return filterType},
            getURLParameterName : function(dataRegionName) { return (dataRegionName || "query") + "." + columnName + "~" + filterType.getURLSuffix();},
            getURLParameterValue : function() { return filterType.isDataValueRequired() ? value : "" }
        };
    }

    var ret = /** @scope LABKEY.Filter */{

		Types : {

            HAS_ANY_VALUE : createFilterType("Has Any Value", "", false),
			EQUAL : createFilterType("Equals", "eq", true),
            DATE_EQUAL : createFilterType("Equals", "dateeq", true),
            DATE_NOT_EQUAL : createFilterType("Does Not Equal", "dateneq", true),
            NEQ_OR_NULL : createFilterType("Does Not Equal", "neqornull", true),
            NOT_EQUAL_OR_MISSING : createFilterType("Does Not Equal", "neqornull", true),
            NEQ : createFilterType("Does Not Equal", "neq", true),
            NOT_EQUAL : createFilterType("Does Not Equal", "neq", true),
            ISBLANK : createFilterType("Is Blank", "isblank", false),
            MISSING : createFilterType("Is Blank", "isblank", false),
            NONBLANK : createFilterType("Is Not Blank", "isnonblank", false),
            NOT_MISSING : createFilterType("Is Not Blank", "isnonblank", false),
            GT : createFilterType("Is Greater Than", "gt", true),
            GREATER_THAN : createFilterType("Is Greater Than", "gt", true),
            DATE_GREATER_THAN : createFilterType("Is Greater Than", "dategt", true),
            LT : createFilterType("Is Less Than", "lt", true),
            LESS_THAN : createFilterType("Is Less Than", "lt", true),
            DATE_LESS_THAN : createFilterType("Is Less Than", "datelt", true),
            GTE : createFilterType("Is Greater Than or Equal To", "gte", true),
            GREATER_THAN_OR_EQUAL : createFilterType("Is Greater Than or Equal To", "gte", true),
            DATE_GREATER_THAN_OR_EQUAL : createFilterType("Is Greater Than or Equal To", "dategte", true),
            LTE : createFilterType("Is Less Than or Equal To", "lte", true),
            LESS_THAN_OR_EQUAL : createFilterType("Is Less Than or Equal To", "lte", true),
            DATE_LESS_THAN_OR_EQUAL : createFilterType("Is Less Than or Equal To", "datelte", true),
            CONTAINS : createFilterType("Contains", "contains", true),
            DOES_NOT_CONTAIN : createFilterType("Does Not Contain", "doesnotcontain", true),
            DOES_NOT_START_WITH : createFilterType("Does Not Start With", "doesnotstartwith", true),
            STARTS_WITH : createFilterType("Starts With", "startswith", true),
            IN : createFilterType("Equals One Of", "in", true, true),
            IN_OR_MISSING : createFilterType("Equals One Of", "inornull", true, true),
            //NOTE: for some reason IN is aliased as EQUALS_ONE_OF.  not sure if this is for legacy purposes or it was determined EQUALS_ONE_OF was a better phrase
            //to follow this pattern I did the same for IN_OR_MISSING
            EQUALS_ONE_OF : createFilterType("Equals One Of", "in", true, true),
            EQUALS_ONE_OF_OR_MISSING : createFilterType("Equals One Of", "inornull", true, true),
            NOT_IN : createFilterType("Does Not Equal Any Of", "notin", true, true),
            NOT_IN_OR_MISSING : createFilterType("Does Not Equal Any Of", "notinornull", true, true),
            CONTAINS_ONE_OF : createFilterType("Contains One Of", "containsoneof", true, true),
            CONTAINS_NONE_OF : createFilterType("Does Not Contain Any Of", "containsnoneof", true, true),
            HAS_MISSING_VALUE : createFilterType("Has a missing value indicator", "hasmvvalue", false),
            DOES_NOT_HAVE_MISSING_VALUE : createFilterType("Does not have a missing value indicator", "nomvvalue", false)
        },

        /** @private create a js object suitable for Query.selectRows, etc */
        appendFilterParams : function (params, filterArray, dataRegionName)
        {
            dataRegionName = dataRegionName || "query";
            params = params || {};
            if (filterArray)
            {
                for (var i = 0; i < filterArray.length; i++)
                {
                    var filter = filterArray[i];
                    // 10.1 compatibility: treat ~eq=null as a NOOP (ref 10482)
                    if (filter.getFilterType().isDataValueRequired() && null == filter.getURLParameterValue())
                        continue;
                    params[filter.getURLParameterName(dataRegionName)] = filter.getURLParameterValue();
                }
            }
            return params;
        },

        /** @private create a js object suitable for QueryWebPart, etc */
        appendAggregateParams : function (params, aggregateArray, dataRegionName)
        {
            dataRegionName = dataRegionName || "query";
            params = params || {};
            if (aggregateArray)
            {
                for (var idx = 0; idx < aggregateArray.length; ++idx)
                {
                    var aggregate = aggregateArray[idx];
                    if (aggregate.type && aggregate.column)
                        params[dataRegionName + '.agg.' + aggregate.column] = aggregate.type;
                }
            }
            return params;
        },


        /**
        * Creates a filter
        * @param {String} columnName String name of the column to filter
        * @param value Value used as the filter criterion
        * @param {LABKEY.Filter#Types} [filterType] Type of filter to apply to the 'column' using the 'value'
		* @example Example: <pre name="code" class="xml">
&lt;script type="text/javascript"&gt;
	function onFailure(errorInfo, options, responseObj)
	{
	    if(errorInfo && errorInfo.exception)
	        alert("Failure: " + errorInfo.exception);
	    else
	        alert("Failure: " + responseObj.statusText);
	}

	function onSuccess(data)
	{
	    alert("Success! " + data.rowCount + " rows returned.");
	}

	LABKEY.Query.selectRows({
		schemaName: 'lists',
		queryName: 'People',
		success: onSuccess,
		failure: onFailure,
		filterArray: [
			LABKEY.Filter.create('FirstName', 'Johnny'),
			LABKEY.Filter.create('Age', 15, LABKEY.Filter.Types.LESS_THAN_OR_EQUAL)
		]
    });
&lt;/script&gt; </pre>
        */

        create : function(columnName, value, filterType)
        {
            if (!filterType)
                filterType = this.Types.EQUAL;
            return getFilter(columnName, value, filterType);
        },

        /**
        * Convert from URL syntax filters to a human readable description, like "Is Greater Than 10 AND Is Less Than 100"
        * @param {String} url URL containing the filter parameters
        * @param {String} dataRegionName String name of the data region the column is a part of
        * @param {String} columnName String name of the column to filter
        * @return {String} human readable version of the filter
         */
        getFilterDescription : function(url, dataRegionName, columnName)
        {
            var params = LABKEY.ActionURL.getParameters(url);
            var result = "";
            var separator = "";
            for (var paramName in params)
            {
                // Look for parameters that have the right prefix
                if (paramName.indexOf(dataRegionName + "." + columnName + "~") == 0)
                {
                    var filterType = paramName.substring(paramName.indexOf("~") + 1);
                    var values = params[paramName];
                    if (!Ext.isArray(values))
                    {
                        values = [values];
                    }
                    // Get the human readable version, like "Is Less Than"
                    var friendly = urlMap[filterType];
                    var displayText;
                    if (!friendly)
                    {
                        displayText = filterType;
                    }
                    else
                    {
                        displayText = friendly.getDisplayText();
                    }

                    for (var j = 0; j < values.length; j++)
                    {
                        // If the same type of filter is applied twice, it will have multiple values
                        result += separator;
                        separator = " AND ";

                        result += displayText;
                        result += " ";
                        result += values[j];
                    }
                }
            }
            return result;
        },

        /** @private Returns a filter type for the urlSuffix. */
        getFilterTypeForURLSuffix : function (urlSuffix)
        {
            return urlMap[urlSuffix];
        }

    };

    var ft = ret.Types;
    var filterTypes = {
        "int":[ft.HAS_ANY_VALUE, ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK, ft.GT, ft.LT, ft.GTE, ft.LTE, ft.IN, ft.IN_OR_MISSING, ft.NOT_IN, ft.NOT_IN_OR_MISSING],
        "string":[ft.HAS_ANY_VALUE, ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK, ft.GT, ft.LT, ft.GTE, ft.LTE, ft.CONTAINS, ft.DOES_NOT_CONTAIN, ft.DOES_NOT_START_WITH, ft.STARTS_WITH, ft.IN, ft.IN_OR_MISSING, ft.NOT_IN, ft.NOT_IN_OR_MISSING, ft.CONTAINS_ONE_OF, ft.CONTAINS_NONE_OF],
        "boolean":[ft.HAS_ANY_VALUE, ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK],
        "float":[ft.HAS_ANY_VALUE, ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK, ft.GT, ft.LT, ft.GTE, ft.LTE, ft.IN, ft.IN_OR_MISSING, ft.NOT_IN, ft.NOT_IN_OR_MISSING],
        "date":[ft.HAS_ANY_VALUE, ft.DATE_EQUAL, ft.DATE_NOT_EQUAL, ft.ISBLANK, ft.NONBLANK, ft.DATE_GREATER_THAN, ft.DATE_LESS_THAN, ft.DATE_GREATER_THAN_OR_EQUAL, ft.DATE_LESS_THAN_OR_EQUAL, ft.IN, ft.IN_OR_MISSING, ft.NOT_IN, ft.NOT_IN_OR_MISSING]
    };

    var defaultFilter = {
        "int": ft.EQUAL,
        "string": ft.STARTS_WITH,
        "boolean": ft.EQUAL,
        "float": ft.EQUAL,
        "date": ft.DATE_EQUAL
    };

    /** @private Returns an Array of filter types that can be used with the given json type ("int", "double", "string", "boolean", "date") */
    ret.getFilterTypesForType = function (type, mvEnabled)
    {
        var types = [];
        if (filterTypes[type])
            types = types.concat(filterTypes[type]);

        if (mvEnabled)
        {
            types.push(ft.HAS_MISSING_VALUE);
            types.push(ft.DOES_NOT_HAVE_MISSING_VALUE);
        }

        return types;
    };

    /** @private Return the default LABKEY.Filter.Type for a json type ("int", "double", "string", "boolean", "date"). */
    ret.getDefaultFilterForType = function (type)
    {
        if (defaultFilter[type])
            return defaultFilter[type];

        return ft.EQUAL;
    };

    return ret;
};

/**
* @name LABKEY.Filter.FilterDefinition
* @description Static class that defines the functions that describe how a particular
*            type of filter is identified and operates.  See {@link LABKEY.Filter}.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=filteringData">Filter via the LabKey UI</a></li>
 *              </ul>
 *           </p>
* @class  Static class that defines the functions that describe how a particular
*            type of filter is identified and operates.  See {@link LABKEY.Filter}.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=filteringData">Filter via the LabKey UI</a></li>
 *              </ul>
 *           </p>
*/

/**#@+
 * @methodOf LABKEY.Filter.FilterDefinition#
*/

/**
* Get the string displayed for this filter.
* @name getDisplayText
* @type String
*/

/**
* Get the ULR suffix used to identify this filter.
* @name getURLSuffix
* @type String
*/

/**
* Get the Boolean that indicates whether a data value is required.
* @name isDataValueRequired
* @type Boolean
*/

/**
* Get the Boolean that indicates whether the filter supports a string with multiple filter values (ie. contains one of, not in, etc).
* @name isMultiValued
* @type Boolean
*/

/**
* Get the LABKEY.Filter.FilterDefinition the represents the opposite of this filter type.
* @name getOpposite
* @type LABKEY.Filter.FilterDefinition
*/

/**#@-*/
