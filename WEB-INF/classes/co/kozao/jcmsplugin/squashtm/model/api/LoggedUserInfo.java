package co.kozao.jcmsplugin.squashtm.model.api;

import java.util.Date;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.jalios.util.Util;

import co.kozao.jcmsplugin.squashtm.util.SquashTmUtils;

public class LoggedUserInfo {
	private static final Logger LOGGER = Logger.getLogger(LoggedUserInfo.class);

	private String fullName;
	private String emailAddr;
	private String createdAt = "";
	private String id;

	public static LoggedUserInfo fromJson(String json) {
		try {
			Gson gson = new Gson();
			LoggedUserInfo loggedUserInfo = (LoggedUserInfo) gson.fromJson(json, LoggedUserInfo.class);
			return loggedUserInfo;
		} catch (Exception e) {
			LOGGER.error("ERROR URL: " + e.getMessage());
		}
		return null;
	}

	public Date getCreatedDate() {
		if (Util.notEmpty(this.createdAt)) {
			return SquashTmUtils.getStandardStringToDate(this.createdAt);
		}
		return null;
	}

	public String toJson() {
		try {
			Gson gson = new Gson();
			return gson.toJson(this);
		} catch (Exception e) {
			LOGGER.error("Unable to serialize LoggedUserInfo", e);
			return null;
		}
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}


	public String getEmailAddr() {
		return emailAddr;
	}

	public void setEmailAddr(String emailAddr) {
		this.emailAddr = emailAddr;
	}

	
	public String getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}


	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

}
