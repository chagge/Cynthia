package com.sogou.qadev.service.cynthia.service;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.sogou.qadev.cache.impl.FieldNameCache;
import com.sogou.qadev.service.cynthia.bean.Action;
import com.sogou.qadev.service.cynthia.bean.Attachment;
import com.sogou.qadev.service.cynthia.bean.ChangeLog;
import com.sogou.qadev.service.cynthia.bean.CommonField;
import com.sogou.qadev.service.cynthia.bean.CommonOption;
import com.sogou.qadev.service.cynthia.bean.Data;
import com.sogou.qadev.service.cynthia.bean.Field;
import com.sogou.qadev.service.cynthia.bean.Field.DataType;
import com.sogou.qadev.service.cynthia.bean.Field.Type;
import com.sogou.qadev.service.cynthia.bean.Flow;
import com.sogou.qadev.service.cynthia.bean.Option;
import com.sogou.qadev.service.cynthia.bean.Stat;
import com.sogou.qadev.service.cynthia.bean.Template;
import com.sogou.qadev.service.cynthia.bean.UUID;
import com.sogou.qadev.service.cynthia.dao.DataAccessSessionMySQL;
import com.sogou.qadev.service.cynthia.factory.DataAccessFactory;
import com.sogou.qadev.service.cynthia.service.ErrorManager.ErrorType;
import com.sohu.rd.td.util.xml.XMLUtil;

public class DataManager
{
	private static DataManager instance = null;

	public static DataManager getInstance()
	{
		if (instance == null)
			instance = new DataManager();

		return instance;
	}

	public String makeResult(String title, String stat, String assignUser, boolean success){
		StringBuffer result = new StringBuffer();
		result.append("<result>");
		result.append("<title>").append(XMLUtil.toSafeXMLString(title)).append("</title>");
		result.append("<status>").append(XMLUtil.toSafeXMLString(stat)).append("</status>");

		if(assignUser == null){
			result.append("<assignUser/>");
		}
		else{
			result.append("<assignUser>").append(XMLUtil.toSafeXMLString(assignUser)).append("</assignUser>");
		}

		result.append("<success>").append(success).append("</success>");
		result.append("</result>");

		return result.toString();
	}

	public String makeModifyStatResult(String title, boolean success){
		StringBuffer result = new StringBuffer();
		result.append("<result>");
		result.append("<title>").append(XMLUtil.toSafeXMLString(title)).append("</title>");
		result.append("<success>").append(success).append("</success>");
		result.append("</result>");

		return result.toString();
	}
	
	public synchronized void addExtFields(UUID templateId, Map<String, CommonField> fieldMap, Map<String, Set<CommonOption>> fieldOptionMap, DataAccessSession das)
	{
		Template template = das.queryTemplate(templateId);
		if(template == null)
			return;

		Set<Field> fieldSet = template.getFields();
		if(fieldSet == null)
			return;

		for(Field field : fieldSet)
		{
			CommonField commonField = new CommonField();
			commonField.setId(field.getId().toString());
			commonField.setName(field.getName());
			commonField.setType(field.getType().toString().split("\\_")[1]);
			if(field.getDataType() != null)
				commonField.setDataType(field.getDataType().toString().split("\\_")[1]);

			fieldMap.put(commonField.getId(), commonField);

			if(field.getType().equals(Field.Type.t_selection))
			{
				if(field.getOptions() != null && field.getOptions().size() > 0)
				{
					fieldOptionMap.put(commonField.getId(), new TreeSet<CommonOption>());

					for(Option option : field.getOptions())
					{
						CommonOption commonOption = new CommonOption();
						commonOption.setId(option.getId().toString());
						commonOption.setName(option.getName());

						fieldOptionMap.get(commonField.getId()).add(commonOption);
					}
				}
			}
			else if(field.getType().equals(Field.Type.t_reference))
			{
				Data[] referenceArray = das.queryTemplateFieldReferences(templateId, field.getId());
				if(referenceArray != null && referenceArray.length > 0)
				{
					fieldOptionMap.put(commonField.getId(), new TreeSet<CommonOption>());

					for(Data reference : referenceArray)
					{
						CommonOption commonOption = new CommonOption();
						commonOption.setId(reference.getId().toString());
						commonOption.setName(reference.getTitle());

						fieldOptionMap.get(commonField.getId()).add(commonOption);
					}
				}
			}
			else if(field.getType().equals(Field.Type.t_attachment))
			{
				Attachment[] attachmentArray = das.queryTemplateFieldAttachments(templateId, field.getId());
				if(attachmentArray != null && attachmentArray.length > 0)
				{
					fieldOptionMap.put(commonField.getId(), new TreeSet<CommonOption>());

					for(Attachment attachment : attachmentArray)
					{
						CommonOption commonOption = new CommonOption();
						commonOption.setId(attachment.getId().toString());
						commonOption.setName(attachment.getName());

						fieldOptionMap.get(commonField.getId()).add(commonOption);
					}
				}
			}
		}
	}

	public String getDataFieldValue(UUID dataId, UUID templateId, UUID fieldId, DataAccessSession das)
	{
		Data data = das.queryData(dataId,templateId);
		if(data == null)
			return null;

		Field field = das.queryField(fieldId , data.getTemplateId());
		if(field == null)
			return null;

		if(field.getType().equals(Field.Type.t_selection))
		{
			if(field.getDataType().equals(Field.DataType.dt_single))
			{
				UUID optionId = data.getSingleSelection(fieldId);
				if(optionId == null)
					return null;

				Option option = field.getOption(optionId);
				if(option != null)
					return "[" + option.getName() + "]";
			}
			else if(field.getDataType().equals(Field.DataType.dt_multiple))
			{
				UUID[] optionIdArray = data.getMultiSelection(fieldId);
				if(optionIdArray == null || optionIdArray.length == 0)
					return null;

				StringBuffer valueStrb = new StringBuffer();
				for(UUID optionId : optionIdArray)
				{
					Option option = field.getOption(optionId);
					if(option == null)
						continue;

					if(valueStrb.length() > 0)
						valueStrb.append(",");

					valueStrb.append("[").append(option.getName()).append("]");
				}

				return valueStrb.toString();
			}
		}
		else if(field.getType().equals(Field.Type.t_reference))
		{
			if(field.getDataType().equals(Field.DataType.dt_single))
			{
				UUID referenceId = data.getSingleReference(fieldId);
				if(referenceId == null)
					return null;

				String[] referTitleArray = new DataAccessSessionMySQL().queryFieldByIds(new UUID[]{referenceId}, "title", templateId);
				if(referTitleArray != null && referTitleArray.length > 0)
					return "[" + referTitleArray[0] + "]";
			}
			else if(field.getDataType().equals(Field.DataType.dt_multiple))
			{
				UUID[] referenceIdArray = data.getMultiReference(fieldId);
				if(referenceIdArray == null || referenceIdArray.length == 0)
					return null;

				StringBuffer valueStrb = new StringBuffer();
				String[] referTitleArray = new DataAccessSessionMySQL().queryFieldByIds(referenceIdArray, "title", templateId);

				for(String title : referTitleArray)
				{
					if(title == null)
						continue;
					if(valueStrb.length() > 0)
						valueStrb.append(",");
					valueStrb.append("[").append(title).append("]");
				}

				return valueStrb.toString();
			}
		}
		else if(field.getType().equals(Field.Type.t_attachment))
		{
			UUID[] attachmentIdArray = data.getAttachments(fieldId);
			if(attachmentIdArray == null || attachmentIdArray.length == 0)
				return null;

			StringBuffer valueStrb = new StringBuffer();

			Attachment[] attachmentArray = das.queryAttachments(attachmentIdArray, false);
			for(Attachment attachment : attachmentArray)
			{
				if(valueStrb.length() > 0)
					valueStrb.append(",");

				valueStrb.append("[").append(attachment.getName()).append("]");
			}

			return valueStrb.toString();
		}
		else if(field.getType().equals(Field.Type.t_input))
		{
			Object value = null;

			if(field.getDataType().equals(Field.DataType.dt_integer))
				value = data.getInteger(fieldId);
			else if(field.getDataType().equals(Field.DataType.dt_long))
				value = data.getLong(fieldId);
			else if(field.getDataType().equals(Field.DataType.dt_float))
				value = data.getFloat(fieldId);
			else if(field.getDataType().equals(Field.DataType.dt_double))
				value = data.getDouble(fieldId);
			else if(field.getDataType().equals(Field.DataType.dt_string) || field.getDataType().equals(Field.DataType.dt_text))
				value = data.getString(fieldId);
			else if(field.getDataType().equals(Field.DataType.dt_timestamp))
				value = data.getDate(fieldId);

			if(value != null)
				return value.toString();
		}

		return null;
	}

	public String getDataStatus(Data data, DataAccessSession das)
	{
		Template template = das.queryTemplate(data.getTemplateId());
		if(template == null)
			return null;

		Flow flow = das.queryFlow(template.getFlowId());
		if(flow == null)
			return null;

		Stat stat = flow.getStat(data.getStatusId());
		if(stat == null)
			return null;

		return stat.getName();
	}

	public UUID getDataTemplateTypeId(Data data, DataAccessSession das)
	{
		Template template = das.queryTemplate(data.getTemplateId());
		if(template == null)
			return null;

		return template.getTemplateTypeId();
	}

	public String getBatchStatusModifyXML(UUID[] dataIdArray,DataAccessSession das){

		Set<String> nodeStatusSet = new HashSet<String>();

		Set<Stat> allStatSet = new LinkedHashSet<Stat>();
		
		for(UUID dataId : dataIdArray)
		{
			Data data = das.queryData(dataId);
			if(data == null)
				continue;

			Template template = das.queryTemplate(data.getTemplateId());
			if(template == null)
				continue;

			if(nodeStatusSet.contains(template.getId() + "|" + data.getStatusId()))
				continue;

			nodeStatusSet.add(template.getId() + "|" + data.getStatusId());

			Flow flow = das.queryFlow(template.getFlowId());
			if(flow == null)
				continue;
			boolean isEditAllow = flow.isEditActionAllow(das.getUsername(), template.getId(), data.getAssignUsername(), data.getActionUser());

			if(isEditAllow){//具有编辑权限的人可以批量修改状态
				Stat[] allStats = flow.getStats();
				allStatSet.addAll(Arrays.asList(allStats));
			}
		}

		StringBuffer strb = new StringBuffer();
		strb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		if(allStatSet.size() == 0)
			strb.append("<stats/>");
		else
		{
			strb.append("<stats>");

			for(Stat stat : allStatSet)
			{
				strb.append("<stat>");
				strb.append("<id>").append(stat.getId().getValue()).append("</id>");
				strb.append("<name>").append(XMLUtil.toSafeXMLString(stat.getName())).append("</name>");
				strb.append("</stat>");
			}

			strb.append("</stats>");
		}

		return strb.toString();
	}
	
	public String getBatchCloseActionsXML(UUID[] dataIdArray,DataAccessSession das)
	{
		Map<String, Set<String>> actionUserMap = new LinkedHashMap<String, Set<String>>();

		Set<String> nodeStatusSet = new HashSet<String>();

		for(UUID dataId : dataIdArray)
		{
			Data data = das.queryData(dataId);
			if(data == null)
				continue;

			Template template = das.queryTemplate(data.getTemplateId());
			if(template == null)
				continue;

			if(nodeStatusSet.contains(template.getId() + "|" + data.getStatusId()))
				continue;

			nodeStatusSet.add(template.getId() + "|" + data.getStatusId());

			Flow flow = das.queryFlow(template.getFlowId());
			if(flow == null)
				continue;
			boolean isEditAllow = flow.isEditActionAllow(das.getUsername(), template.getId(), data.getAssignUsername(), data.getActionUser());
			Set<Action> actionSet = new LinkedHashSet<Action>();

			if(isEditAllow){//具有编辑权限的人可以批量关闭BUG add by lyl
				Action[] endActions = flow.getEndActions();
				for(int i=0;endActions!=null && i<endActions.length;i++){
					actionSet.add(endActions[i]);
				}
			}
			for(Action action : actionSet)
			{
				String actionName = action.getName();
				if(action.getBeginStatId() == null)
					actionName = "激活--" + actionName;

				actionUserMap.put(actionName, new LinkedHashSet<String>());

				String[] userArray = flow.queryNodeStatAssignUsers(template.getId(), action.getEndStatId());
				if(userArray != null)
					actionUserMap.get(actionName).addAll(Arrays.asList(userArray));
			}
		}

		StringBuffer strb = new StringBuffer();
		strb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		if(actionUserMap.size() == 0)
			strb.append("<actions/>");
		else
		{
			strb.append("<actions>");

			for(String actionName : actionUserMap.keySet())
			{
				strb.append("<action>");
				strb.append("<name>").append(XMLUtil.toSafeXMLString(actionName)).append("</name>");
				Set<String> userSet = actionUserMap.get(actionName);
				if(userSet == null || userSet.size() == 0)
					strb.append("<users/>");
				else
				{
					strb.append("<users>");
					for(String user : userSet)
						strb.append("<user>").append(XMLUtil.toSafeXMLString(user)).append("</user>");
					strb.append("</users>");
				}
				strb.append("</action>");
			}

			strb.append("</actions>");
		}

		return strb.toString();
	}

	public String getActionsXML(UUID[] dataIdArray, DataAccessSession das)
	{
		Map<String, Set<String>> actionUserMap = new LinkedHashMap<String, Set<String>>();

		Set<String> nodeStatusSet = new HashSet<String>();

		for(UUID dataId : dataIdArray)
		{
			Data data = das.queryData(dataId);
			if(data == null)
				continue;

			Template template = das.queryTemplate(data.getTemplateId());
			if(template == null)
				continue;

			if(nodeStatusSet.contains(template.getId() + "|" + data.getStatusId()))
				continue;

			nodeStatusSet.add(template.getId() + "|" + data.getStatusId());

			Flow flow = das.queryFlow(template.getFlowId());
			if(flow == null)
				continue;

			boolean isEditAllow = flow.isEditActionAllow(das.getUsername(), template.getId(), data.getAssignUsername(), data.getActionUser());
			if(isEditAllow)
			{
				String[] assignUserArray = flow.queryNodeStatAssignUsers(template.getId(), data.getStatusId());
				if(assignUserArray != null)
				{
					if(!actionUserMap.containsKey("编辑"))
						actionUserMap.put("编辑", new LinkedHashSet<String>());

					actionUserMap.get("编辑").addAll(Arrays.asList(assignUserArray));
				}
			}

			Set<Action> actionSet = new LinkedHashSet<Action>();

			Action[] statActionArray = flow.queryStatActions(data.getStatusId());
			if(statActionArray == null || statActionArray.length == 0)
			{
				Action[] userNodeBeginActionArray = flow.queryUserNodeBeginActions(das.getUsername(), template.getId());
				for(int i = 0; userNodeBeginActionArray != null && i < userNodeBeginActionArray.length; i++)
					actionSet.add(userNodeBeginActionArray[i]);
			}
			else
			{
				Action[] userNodeStatActionArray = flow.queryUserNodeStatActions(das.getUsername(), template.getId(), data.getStatusId());
				for(int i = 0; userNodeStatActionArray != null && i < userNodeStatActionArray.length; i++)
					actionSet.add(userNodeStatActionArray[i]);
			}

			for(Action action : actionSet)
			{
				String actionName = action.getName();
				if(action.getBeginStatId() == null)
					actionName = "激活--" + actionName;

				actionUserMap.put(actionName, new LinkedHashSet<String>());

				String[] userArray = flow.queryNodeStatAssignUsers(template.getId(), action.getEndStatId());
				if(userArray != null)
					actionUserMap.get(actionName).addAll(Arrays.asList(userArray));
			}
		}

		StringBuffer strb = new StringBuffer();
		strb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		if(actionUserMap.size() == 0)
			strb.append("<actions/>");
		else
		{
			strb.append("<actions>");

			for(String actionName : actionUserMap.keySet())
			{
				strb.append("<action>");
				strb.append("<name>").append(XMLUtil.toSafeXMLString(actionName)).append("</name>");
				Set<String> userSet = actionUserMap.get(actionName);
				if(userSet == null || userSet.size() == 0)
					strb.append("<users/>");
				else
				{
					strb.append("<users>");
					for(String user : userSet)
						strb.append("<user>").append(XMLUtil.toSafeXMLString(user)).append("</user>");
					strb.append("</users>");
				}
				strb.append("</action>");
			}

			strb.append("</actions>");
		}

		return strb.toString();
	}

	/**
	 * @function：
	 * @modifyTime：2013-9-9 下午6:22:29
	 * @author：李明
	 * @email: liming@sogou-inc.com
	 * @param templateTypeId templateTypeId 为空时为查询所有表单
	 * @param das
	 * @return
	 */
	public Template[] queryUserTemplates(UUID templateTypeId, DataAccessSession das)
	{
		Template[] templateArray = das.queryAllTemplates();
		if(templateArray == null || templateArray.length == 0)
			return new Template[0];

		Set<Template> templateSet = new LinkedHashSet<Template>();
		for(Template template : templateArray)
		{
			if(templateTypeId != null && !template.getTemplateTypeId().equals(templateTypeId))
				continue;
			
			Flow flow = das.queryFlow(template.getFlowId());
			if(flow == null)
				continue;

			Action[] actionArray = flow.queryUserNodeBeginActions(das.getUsername(), template.getId());
			if(actionArray != null && actionArray.length > 0)
				templateSet.add(template);
		}

		return templateSet.toArray(new Template[0]);
	}

	/**
	 * @function：
	 * @modifyTime：2013-9-9 下午6:22:29
	 * @author：李明
	 * @email: liming@sogou-inc.com
	 * @param das
	 * @return
	 */
	public Template[] queryUserTemplates(DataAccessSession das)
	{
		Template[] templateArray = das.queryAllTemplates();
		if(templateArray == null || templateArray.length == 0)
			return new Template[0];

		Set<Template> templateSet = new LinkedHashSet<Template>();
		for(Template template : templateArray)
		{
			Flow flow = das.queryFlow(template.getFlowId());
			if(flow == null)
				continue;
			
			Action[] actionArray = flow.queryUserNodeBeginActions(das.getUsername(), template.getId());
			if(actionArray != null && actionArray.length > 0)
				templateSet.add(template);
		}

		return templateSet.toArray(new Template[0]);
	}

	//根据日期判断是否为选择数据 
	private static boolean isSelectedData(String startDate,String endDate,String date)
	{
		if(startDate ==null&&endDate == null)
			return false;
		if(startDate == null&&(date.compareTo(endDate)<=0))
			return true;
		else if(endDate == null&&(date.compareTo(startDate)>=0))
			return true;
		else if(date.compareTo(startDate)>=0&&date.compareTo(endDate)<=0)
			return true;	
		else 
			return false;
	}

	private static String transferTime2String(Timestamp time)
	{
		SimpleDateFormat  sdf = new SimpleDateFormat ("yyyy-MM-dd");
		return sdf.format(time);
	}

	//查询状态名
	private static String queryStatName(Stat[] stats,UUID statId)
	{
		for(Stat stat : stats)
		{
			if(stat.getId().equals(statId))
				return stat.getName();
		}
		return "";
	}

	//查询单选值 
	private static String querySingleOptionName(Set<Option> options,UUID optionId)
	{
		for(Option option : options)
		{
			if(option.getId().equals(optionId))
				return option.getName();
		}
		return "";
	}
	//查询多选值
	private static String queryMultiOptionName(Set<Option> options,UUID[] optionIds)
	{
		String allOptions = "";
		for(UUID uuid : optionIds){
			for(Option option : options){
				if(option.getId().equals(uuid))
					allOptions += option.getName()+",";
			}
		}
		if(allOptions.length()>0)
			allOptions = allOptions.substring(0,allOptions.length()-1);
		return allOptions;
	}

	private static String getFieldData(Field field,Data data){
		String returndata = "";
		if(field.getType()==Type.t_input){
			if(field.getDataType()==DataType.dt_timestamp){
				if(data.getDate(field.getId())!=null)
					returndata = "<field><id>" +field.getId().toString()+ "</id><name>" + XMLUtil.toSafeXMLString(field.getName()) + "</name><value>" + XMLUtil.toSafeXMLString(data.getDate(field.getId()).toString()) + "</value></field>";
			}else if(field.getDataType() == DataType.dt_double){
				if(data.getDouble(field.getId())!=null)
					returndata = "<field><id>" +field.getId().toString()+ "</id><name>" + XMLUtil.toSafeXMLString(field.getName()) + "</name><value>" + XMLUtil.toSafeXMLString(data.getDouble(field.getId()).toString()) + "</value></field>";
			}else if(field.getDataType() == DataType.dt_float){
				if(data.getFloat(field.getId())!=null)
					returndata = "<field><id>" +field.getId().toString()+ "</id><name>" + XMLUtil.toSafeXMLString(field.getName()) + "</name><value>" + XMLUtil.toSafeXMLString(data.getFloat(field.getId()).toString()) + "</value></field>";
			}else if(field.getDataType() == DataType.dt_integer){
				if(data.getInteger(field.getId())!=null)
					returndata = "<field><id>" +field.getId().toString()+ "</id><name>" + XMLUtil.toSafeXMLString(field.getName()) + "</name><value>" + XMLUtil.toSafeXMLString(data.getInteger(field.getId()).toString()) + "</value></field>";
			}else if(field.getDataType() == DataType.dt_float){
				if(data.getFloat(field.getId())!=null)
					returndata = "<field><id>" +field.getId().toString()+ "</id><name>" + XMLUtil.toSafeXMLString(field.getName()) + "</name><value>" + XMLUtil.toSafeXMLString(data.getFloat(field.getId()).toString()) + "</value></field>";
				
			}else if(field.getDataType() == DataType.dt_long){
				if(data.getLong(field.getId())!=null)
					returndata = "<field><id>" +field.getId().toString()+ "</id><name>" + XMLUtil.toSafeXMLString(field.getName()) + "</name><value>" + XMLUtil.toSafeXMLString(data.getLong(field.getId()).toString()) + "</value></field>";
			}else {  //string
				if(data.getString(field.getId())!=null)
					returndata = "<field><id>" +field.getId().toString()+ "</id><name>" + XMLUtil.toSafeXMLString(field.getName()) + "</name><value>" + XMLUtil.toSafeXMLString(data.getString(field.getId())) + "</value></field>";
			}
			
		}else if(field.getType() == Type.t_selection){
			if(field.getDataType() == DataType.dt_single){
				if(data.getSingleSelection(field.getId())!=null)
					returndata = "<field><id>" +field.getId().toString()+ "</id><name>" + XMLUtil.toSafeXMLString(field.getName()) + "</name><value>" + XMLUtil.toSafeXMLString(querySingleOptionName(field.getOptions(), data.getSingleSelection(field.getId()))) + "</value></field>";
			}else if(field.getDataType() == DataType.dt_multiple){
				if(data.getMultiSelection(field.getId())!=null)
					returndata = "<field><id>" +field.getId().toString()+ "</id><name>" + XMLUtil.toSafeXMLString(field.getName()) + "</name><value>" + XMLUtil.toSafeXMLString(queryMultiOptionName(field.getOptions(), data.getMultiSelection(field.getId()))) + "</value></field>";
			}
		}
		if(returndata == "")  //表示没有值 返回空
			returndata = "<field><id>" +field.getId().toString()+ "</id><name>" + XMLUtil.toSafeXMLString(field.getName()) + "</name><value></value></field>";
		return returndata;
	}

	private static String getLogData(ChangeLog log,Flow flow){
		
		String returndata = "";
		try{
			String logActionName = flow.getAction(log.getActionId()) == null?"编辑" :flow.getAction(log.getActionId()).getName();
			returndata = "<log>"
					   + "<createUser>"	+ XMLUtil.toSafeXMLString(log.getCreateUser()) + "</createUser>"
					   + "<createTime>"	+ XMLUtil.toSafeXMLString(transferTime2String(log.getCreateTime())) + "</createTime>"
					   + "<actionName>"	+ XMLUtil.toSafeXMLString(logActionName) + "</actionName>"
					   + "<actionComment>"	+ XMLUtil.toSafeXMLString(log.getActionComment()) + "</actionComment>"
					   + "</log>";
		}catch(Exception e){
			returndata = "<log></log>";
			e.printStackTrace();
		}
		return returndata;
	}

	/**
	 * @function：返回基本数据xml 
	 * @modifyTime：2013-10-24 下午5:18:08
	 * @author：李明
	 * @email: liming@sogou-inc.com
	 * @param das
	 * @param templateIdStr
	 * @param startTime
	 * @return
	 */
	public static String getDataXML(DataAccessSession das,String templateIdStr,String startTime)
	{
		StringBuffer  returnXml = new StringBuffer();
		returnXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><root>");

		UUID templateId = DataAccessFactory.getInstance().createUUID(templateIdStr);
		Template template = das.queryTemplate(templateId);
		Flow flow = das.queryFlow(template.getFlowId());
			
		Stat[] stats = flow.getStats();   //状态
		Set<Field> allFields = template.getFields();
		
		Timestamp startTimestamp = null;
		try {
			startTimestamp = new Timestamp(new SimpleDateFormat("yyyy-MM-dd").parse(java.sql.Date.valueOf(startTime).toLocaleString()).getTime());
		} catch (ParseException e) {
			e.printStackTrace();
		}
		if(startTimestamp == null)
			return ErrorManager.getErrorXml(ErrorType.time_parse_error);
		
		Data[] templateDatas = das.queryTemplateDatas(templateId, true, startTimestamp, null);
		
		for(Data data : templateDatas)
		{
			String createTime = transferTime2String(data.getCreateTime());
			if(startTime!=null && isSelectedData(startTime,null,createTime))
			{
				returnXml.append("<fields>");
				returnXml.append("<field><name>编号</name><value>").append(XMLUtil.toSafeXMLString(data.getId().toString())).append("</value></field>");
				returnXml.append("<field><name>标题</name><value>").append(XMLUtil.toSafeXMLString(data.getTitle())).append("</value></field>");
				returnXml.append("<field><name>创建人员</name><value>").append(XMLUtil.toSafeXMLString(data.getCreateUsername())).append("</value></field>");
				returnXml.append("<field><name>创建时间</name><value>").append(XMLUtil.toSafeXMLString(data.getCreateTime().toString())).append("</value></field>");
				returnXml.append("<field><name>上次修改时间</name><value>").append(XMLUtil.toSafeXMLString(data.getLastModifyTime().toString())).append("</value></field>");
				returnXml.append("<field><name>当前状态</name><value>").append(XMLUtil.toSafeXMLString(queryStatName(stats, data.getStatusId()))).append("</value></field>");
				returnXml.append("<field><name>描述</name><value>").append(XMLUtil.toSafeXMLString(data.getDescription())).append("</value></field>");
				returnXml.append("<field><name>当前指派</name><value>").append(XMLUtil.toSafeXMLString(data.getAssignUsername())).append("</value></field>");
				
				for(Field field :allFields){
					if(field.getName()==null || field.getName().equals("null"))
						continue;
					returnXml.append(getFieldData(field, data));
				}
				
				ChangeLog[] allLogs = data.getChangeLogs();
				returnXml.append("<logs>");
				for(ChangeLog log : allLogs){
					returnXml.append(getLogData(log, flow));
				}
				returnXml.append("</logs>");
				returnXml.append("</fields>");
			}
		}
		
		returnXml.append("</root>");
		return returnXml.toString();
	}
	
	
	/**
	 * @function：返回基本数据xml 
	 * @modifyTime：2013-10-24 下午5:18:08
	 * @author：李明
	 * @email: liming@sogou-inc.com
	 * @param das
	 * @param templateIdStr
	 * @param startTime
	 * @return
	 */
	public static String getDataXMLNew(DataAccessSession das,String templateIdStr,String startTime, String endTime,boolean needLog)
	{
		StringBuffer  returnXml = new StringBuffer();
		returnXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><root>");

		UUID templateId = DataAccessFactory.getInstance().createUUID(templateIdStr);
		Template template = das.queryTemplate(templateId);
		Flow flow = das.queryFlow(template.getFlowId());
			
		Stat[] stats = flow.getStats();   //状态
		Set<Field> allFields = template.getFields();
		
		Timestamp startTimestamp = null;
		try {
			startTimestamp = new Timestamp(new SimpleDateFormat("yyyy-MM-dd").parse(java.sql.Date.valueOf(startTime).toLocaleString()).getTime());
		} catch (ParseException e) {
			e.printStackTrace();
		}
		if(startTimestamp == null)
			return ErrorManager.getErrorXml(ErrorType.time_parse_error);
		
		Timestamp endTimestamp = null;
		if (endTime != null) {
			try {
				endTimestamp = new Timestamp(new SimpleDateFormat("yyyy-MM-dd").parse(java.sql.Date.valueOf(endTime).toLocaleString()).getTime());
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
			
		if (endTimestamp == null) {
			endTimestamp = new Timestamp(System.currentTimeMillis());
		}
		
		Data[] templateDatas = das.queryTemplateDatas(templateId, needLog, startTimestamp, endTimestamp);
		
		returnXml.append("<datas>");
		for(Data data : templateDatas)
		{
			String createTime = transferTime2String(data.getCreateTime());
			if(startTime!=null && isSelectedData(startTime,null,createTime))
			{
				returnXml.append("<data>");
				returnXml.append("<field><name>编号</name><value>").append(XMLUtil.toSafeXMLString(data.getId().toString())).append("</value></field>");
				returnXml.append("<field><name>标题</name><value>").append(XMLUtil.toSafeXMLString(data.getTitle())).append("</value></field>");
				returnXml.append("<field><name>创建人员</name><value>").append(XMLUtil.toSafeXMLString(data.getCreateUsername())).append("</value></field>");
				returnXml.append("<field><name>创建时间</name><value>").append(XMLUtil.toSafeXMLString(data.getCreateTime().toString())).append("</value></field>");
				returnXml.append("<field><name>上次修改时间</name><value>").append(XMLUtil.toSafeXMLString(data.getLastModifyTime().toString())).append("</value></field>");
				returnXml.append("<field><name>当前状态</name><value>").append(XMLUtil.toSafeXMLString(queryStatName(stats, data.getStatusId()))).append("</value></field>");
				returnXml.append("<field><name>描述</name><value>").append(XMLUtil.toSafeXMLString(data.getDescription())).append("</value></field>");
				returnXml.append("<field><name>当前指派</name><value>").append(XMLUtil.toSafeXMLString(data.getAssignUsername())).append("</value></field>");
				
				for(Field field :allFields){
					if(field.getName()==null || field.getName().equals("null"))
						continue;
					returnXml.append(getFieldData(field, data));
				}
				
				ChangeLog[] allLogs = data.getChangeLogs();
				returnXml.append("<logs>");
				for(ChangeLog log : allLogs){
					returnXml.append(getLogData(log, flow));
				}
				returnXml.append("</logs>");
				returnXml.append("</data>");
			}
		}
		returnXml.append("</datas>");
		returnXml.append("</root>");
		return returnXml.toString();
	}
	
	
	/**
	 * @function：查询数据库中是否有类似数据
	 * @modifyTime：2013-10-16 下午7:58:39
	 * @author：李明
	 * @email: liming@sogou-inc.com
	 * @param dataValue
	 * @param templateId  null表示查询所有数据
	 * @param field
	 * @return
	 */
	public boolean isDataExist(String dataValue , UUID templateId , Field field){
		if (templateId == null || field == null) 
			return false;
	
		if(dataValue==null||dataValue.indexOf("/")<=0)
			return false;
		
		dataValue = dataValue.substring(0,dataValue.lastIndexOf("/"));
		
		String fieldColName = FieldNameCache.getInstance().getFieldName(field.getId(), templateId);
		
		return new DataAccessSessionMySQL().isDataExist(dataValue, templateId , fieldColName);
		
	}
}
