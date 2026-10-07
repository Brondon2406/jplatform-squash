<%@ page import="co.kozao.jcmsplugin.squashtm.util.SquashTmConstants" %>
<%@ page import="co.kozao.jcmsplugin.squashtm.util.SquashTmUtils" %>
<%@ include file='/jcore/doInitPage.jspf' %>
<%
  String itValue = (String) request.getAttribute("itValue");
  String itDefault = (String) request.getAttribute("itDefault");
  itValue = Util.notEmpty(itValue) ? itValue : itDefault;
  String itLabel = (String) request.getAttribute("itLabel");
  String itShortKey = (String) request.getAttribute("itShortKey");
  String itDesc = (String) request.getAttribute("itDesc");
  String itDescription = glp("jcmsplugin.squashtm.provider.authentication-mode.description");
  
  String[] authenticationsOptionsValues = new String[] {SquashTmConstants.SQUASHTM_API_AUTHENTICATION_MODE_BASIC, SquashTmConstants.SQUASHTM_API_AUTHENTICATION_MODE_TOKEN};
  String[] authenticationsOptionsLabels = new String[] {glp("jcmsplugin.squashtm.provider.authentication-mode.basic"), glp("jcmsplugin.squashtm.provider.authentication-mode.token")};
%>

<jalios:field  name="propValue" label="<%= itLabel %>" value="<%= itValue %>" tooltip="<%= itDesc %>" description="<%= itDescription %>" required="true">
   <jalios:control settings="<%= new EnumerateSettings().select().enumLabels(authenticationsOptionsLabels).enumValues(authenticationsOptionsValues) %>" />
</jalios:field>
<input type='hidden' name='propName' value='<%= itShortKey %>'/>