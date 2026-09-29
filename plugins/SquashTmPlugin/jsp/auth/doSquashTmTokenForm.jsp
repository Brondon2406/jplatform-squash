<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="co.kozao.jcmsplugin.squashtm.handler.SquashTmTokenFormHandler" %>
<%@ include file='/jcore/doInitPage.jspf' %><%
%><jsp:useBean id="tokenHandler" scope="page" class="co.kozao.jcmsplugin.squashtm.handler.SquashTmTokenFormHandler"><%
%><jsp:setProperty name="tokenHandler" property="request"  value="<%= request %>"/><%
%><jsp:setProperty name="tokenHandler" property="response" value="<%= response %>"/><%
%><jsp:setProperty name="tokenHandler" property="*" /><%
%></jsp:useBean><%

if (tokenHandler.validate()) {
    return;
}
%>

<jalios:modal title="jcmsplugin.squashtm.token-form.title" formHandler="<%= tokenHandler %>" op="opValidateToken" button="jcmsplugin.squashtm.token-form.submit" css="modal-md" 
		url="plugins/SquashTmPlugin/jsp/auth/doSquashTmTokenForm.jsp">

<jalios:field name="token" label="jcmsplugin.squashtm.token-form.label" required="true" value="<%= tokenHandler.getToken() %>" resource="field-vertical">
        <jalios:control type="<%= ControlType.PASSWORD %>" settings='<%= new TextFieldSettings().placeholder("jcmsplugin.squashtm.token-form.label.help") %>' />
    </jalios:field>
</jalios:modal>