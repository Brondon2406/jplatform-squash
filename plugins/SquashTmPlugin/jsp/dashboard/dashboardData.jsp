<%@ page contentType="application/json; charset=UTF-8" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="com.google.gson.JsonObject" %>
<%@ page import="co.kozao.jcmsplugin.squashtm.handler.SquashTmDashboardHandler" %>
<%@ include file="/jcore/doInitPage.jspf" %>
<%
	// Point d'entrée AJAX du dashboard Squash TM.
	// Architecture : JSP → Handler → Manager → Service
	//   op=projects : liste des projets accessibles
	//   op=data     : indicateurs filtrés
	//   op=export   : export CSV
	response.setCharacterEncoding("UTF-8");
	response.setHeader("Cache-Control", "no-store");
%>
<jsp:useBean id="dashboardHandler" scope="page" class="co.kozao.jcmsplugin.squashtm.handler.SquashTmDashboardHandler">
	<jsp:setProperty name="dashboardHandler" property="request"  value="<%= request %>"/>
	<jsp:setProperty name="dashboardHandler" property="response" value="<%= response %>"/>
	<jsp:setProperty name="dashboardHandler" property="*" />
</jsp:useBean>
<%
	if (loggedMember == null) {
		response.setStatus(403);
		out.print("{\"error\":\"Accès interdit.\"}");
		return;
	}

	String op = dashboardHandler.getOp();
	if (Util.isEmpty(op)) {
		op = request.getParameter("op");
	}

	try {

		if ("projects".equals(op)) {

			response.setContentType("application/json; charset=UTF-8");
			out.print(dashboardHandler.getProjectsJson());

		} else if ("data".equals(op)) {

			response.setContentType("application/json; charset=UTF-8");
			out.print(dashboardHandler.getDashboardJson());

		} else if ("export".equals(op)) {

			String csv = dashboardHandler.getExportCsv();
			response.setContentType("text/csv; charset=UTF-8");
			response.setHeader("Content-Disposition",
				"attachment; filename=\"" + dashboardHandler.getExportFileName() + "\"");
			out.print(csv);

		} else {

			response.setStatus(400);
			out.print("{\"error\":\"Opération inconnue.\"}");
		}

	} catch (Exception e) {

		org.apache.log4j.Logger.getLogger("co.kozao.jcmsplugin.squashtm.dashboard")
			.error("Erreur du dashboard Squash TM : " + e.getMessage(), e);

		JsonObject error = new JsonObject();
		error.addProperty("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());

		response.setStatus(500);
		response.setContentType("application/json; charset=UTF-8");
		out.print(error.toString());
	}
%>