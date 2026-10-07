<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="co.kozao.jcmsplugin.squashtm.handler.SquashTmBasicFormHandler" %>
<%@ include file="/jcore/doInitPage.jspf" %>

<jsp:useBean id="basicHandler" scope="page" class="co.kozao.jcmsplugin.squashtm.handler.SquashTmBasicFormHandler">
    <jsp:setProperty name="basicHandler" property="request" value="<%= request %>" />
    <jsp:setProperty name="basicHandler" property="response" value="<%= response %>" />
    <jsp:setProperty name="basicHandler" property="*" />
</jsp:useBean>

<% if (basicHandler.validate()) { %>
<%@ include file="/jcore/modal/modalRedirect.jspf" %><%
    return;
}
%>

<jalios:modal title="jcmsplugin.squashtm.basic-form.title" formHandler="<%= basicHandler %>" op="opSign" button="jcmsplugin.squashtm.basic-form.submit" css="modal-md"
    url="plugins/SquashTmPlugin/jsp/auth/doSquashTmBasicForm.jsp">

    <jalios:field name="username" label="jcmsplugin.squashtm.basic-form.username" required="true" value="<%= basicHandler.getUsername() %>" resource="field-vertical">
        <jalios:control type="<%= ControlType.TEXTFIELD %>" settings='<%= new TextFieldSettings().placeholder("jcmsplugin.squashtm.basic-form.username") %>' />
    </jalios:field>

    <jalios:field name="password" label="jcmsplugin.squashtm.basic-form.password" required="true" value="<%= basicHandler.getPassword() %>" resource="field-vertical">
        <jalios:control type="<%= ControlType.PASSWORD %>" settings='<%= new TextFieldSettings().placeholder("jcmsplugin.squashtm.basic-form.password") %>' />
    </jalios:field>

</jalios:modal>