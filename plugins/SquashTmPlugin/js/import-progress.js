document.addEventListener("DOMContentLoaded", function() {

    // =============== ÉLÉMENTS DE LA BARRE DE PROGRESSION ==============

    var importForm = document.getElementById("squashTmImportForm");

    var progressContainer = document.getElementById("squashTmProgressContainer");
    var progressBar = document.getElementById("squashTmProgressBar");
    var progressBarContainer = document.getElementById("squashTmProgressBarContainer");
    var progressPercentage = document.getElementById("squashTmProgressPercentage");
    var progressMessage = document.getElementById("squashTmProgressMessage");
    var progressStage = document.getElementById("squashTmProgressStage");
    var progressElapsed = document.getElementById("squashTmProgressElapsed");
    var progressResult = document.getElementById("squashTmProgressResult");
    // Éléments du rapport final.
    var reportSubModules = document.getElementById("squashTmReportSubModules");
    var reportTestSteps = document.getElementById("squashTmReportTestSteps");
    var importReport = document.getElementById("squashTmImportReport");
    var reportProjectName = document.getElementById("squashTmReportProjectName");
    var reportStatus = document.getElementById("squashTmReportStatus");

    var reportProjects = document.getElementById("squashTmReportProjects");
    var reportModules = document.getElementById("squashTmReportModules");
    var reportViews = document.getElementById("squashTmReportViews");
    var reportFeatures = document.getElementById("squashTmReportFeatures");
    var reportRequirements = document.getElementById("squashTmReportRequirements");
    var reportTestCases = document.getElementById("squashTmReportTestCases");

    var reportDuration = document.getElementById("squashTmReportDuration");
    var reportErrors = document.getElementById("squashTmReportErrors");
    var reportErrorSection = document.getElementById("squashTmReportErrorSection");
    var reportErrorList = document.getElementById("squashTmReportErrorList");


    // Éléments du téléchargement de la version à jour du module.
    var reportDownloadButton = document.getElementById("squashTmReportDownloadButton");
    var reportDownloadLabel = document.getElementById("squashTmReportDownloadLabel");
    var reportDownloadError = document.getElementById("squashTmReportDownloadError");
    var reportDownloadProject = "";
    var downloading = false;

    var polling = false;
    var pollingTimer = null;

    // =============== MISE À JOUR DE L'AFFICHAGE ==============

    /**
     * Met à jour les éléments visuels de la progression.
     *
     * @param {Object} data données reçues du serveur
     */
    function updateImportProgress(data) {

        if (!data) {
            return;
        }

        var percentage = Math.max(
            0,
            Math.min(data.completed ? 100 : 100, Number(data.percentage) || 0)
        );

        if (progressBar) {
            progressBar.style.width = percentage + "%";
        }

        if (progressPercentage) {
            progressPercentage.textContent = percentage + " %";
        }

        if (progressBarContainer) {
            progressBarContainer.setAttribute("aria-valuenow", percentage);
        }

        if (progressMessage) {
            progressMessage.textContent =
                data.message || "Importation en cours...";
        }

        if (progressStage) {
            progressStage.textContent = data.stage || "En cours";
        }

        if (progressElapsed) {
            var seconds = Math.floor(
                (Number(data.elapsedTime) || 0) / 1000
            );

            progressElapsed.textContent = seconds + " s";
        }


        if (data.completed) {

            if (progressResult) {
                progressResult.style.display = "block";

                progressResult.textContent = data.success
                    ? "Importation terminée avec succès."
                    : "Importation terminée avec des erreurs. Consultez le détail ci-dessus.";

                progressResult.className = data.success
                    ? "alert alert-success"
                    : "alert alert-danger";
            }


            /**
             * L'importation est terminée, avec ou sans erreurs.
             * La progression doit donc atteindre 100 % dans les deux cas.
             */
            if (progressBar) {
                progressBar.style.width = "100%";
            }

            if (progressPercentage) {
                progressPercentage.textContent = "100 %";
            }

            if (progressBarContainer) {
                progressBarContainer.setAttribute("aria-valuenow", "100");
            }

        }
    }

    /**
     * Affiche le rapport final de l'importation.
     *
     * @param {Object} data données reçues du serveur
     */
    function showImportReport(data) {
        if (!importReport) {
            return;
        }

        var success = data.success === true;

        if (reportProjectName) {
            reportProjectName.textContent = data.projectName || "Non renseigné";
        }

        if (reportStatus) {
            reportStatus.textContent = success
                ? "Importation terminée avec succès."
                : "Importation terminée avec des erreurs.";

            reportStatus.className = success
                ? "squashtm-import-status alert alert-success"
                : "squashtm-import-status alert alert-danger";
        }

        // Un seul projet est concerné par le fichier importé.
        if (reportProjects) {
            reportProjects.textContent = data.projectName ? "1" : "0";
        }

        if (reportModules) {
            reportModules.textContent = data.moduleCount ?? 0;
        }

        if (reportSubModules) {
            reportSubModules.textContent = data.subModuleCount ?? 0;
        }

        if (reportViews) {
            reportViews.textContent = data.viewCount ?? 0;
        }

        if (reportFeatures) {
            reportFeatures.textContent = data.featureCount ?? 0;
        }

        if (reportRequirements) {
            reportRequirements.textContent = data.requirementCount ?? 0;
        }

        if (reportTestCases) {
            reportTestCases.textContent = data.testCaseCount ?? 0;
        }

        if (reportTestSteps) {
            reportTestSteps.textContent = data.testStepCount ?? 0;
        }

        if (reportDuration) {
            var seconds = Math.floor((Number(data.elapsedTime) || 0) / 1000);
            reportDuration.textContent = seconds + " s";
        }

        // Rassembler les incohérences et les erreurs techniques.
        var errors = [];

        if (Array.isArray(data.inconsistencies)) {
            errors = errors.concat(data.inconsistencies);
        }

        if (Array.isArray(data.technicalErrors)) {
            errors = errors.concat(data.technicalErrors);
        }

        if (reportErrors) {
            reportErrors.textContent = String(errors.length);
        }

        if (reportErrorList) {
            reportErrorList.replaceChildren();

            errors.forEach(function(error) {
                var item = document.createElement("li");
                item.textContent = error;
                reportErrorList.appendChild(item);
            });
        }

        if (reportErrorSection) {
            reportErrorSection.style.display =
                errors.length > 0 ? "block" : "none";
        }

        // Bouton affiché uniquement pour un projet qui existait déjà dans Squash TM.
        updateDownloadButton(data.projectName, data.existingProjectReused === true);

        // Masquer la progression et afficher le rapport.
        if (progressContainer) {
            progressContainer.style.display = "none";
        }

        importReport.style.display = "block";
    }

    // =============== TÉLÉCHARGEMENT DE LA VERSION À JOUR DU MODULE ==============

    function updateDownloadButton(projectName, existingProject) {

        if (!reportDownloadButton) {
            return;
        }

        reportDownloadProject = projectName ? String(projectName).trim() : "";

        if (reportDownloadError) {
            reportDownloadError.textContent = "";
            reportDownloadError.style.display = "none";
        }

        reportDownloadButton.style.display =
            (existingProject === true && reportDownloadProject !== "") ? "" : "none";
    }

    /**
     * Extrait le nom du fichier de l'en-tête Content-Disposition.
     */
    function getDownloadFileName(contentDisposition, fallbackName) {

        if (!contentDisposition) {
            return fallbackName;
        }

        var utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition);

        if (utf8Match && utf8Match[1]) {
            try {
                return decodeURIComponent(utf8Match[1]);
            } catch (e) {
                // Ignoré : le nom simple est utilisé ci-dessous.
            }
        }

        var plainMatch = /filename="?([^";]+)"?/i.exec(contentDisposition);

        return plainMatch && plainMatch[1] ? plainMatch[1] : fallbackName;
    }

    /**
     * Télécharge la version à jour du module du projet depuis Squash TM.
     */
    function downloadProjectModule() {

        if (downloading || reportDownloadProject === "") {
            return;
        }

        downloading = true;

        var originalLabel = reportDownloadLabel ? reportDownloadLabel.textContent : "";

        reportDownloadButton.disabled = true;

        if (reportDownloadLabel) {
            reportDownloadLabel.textContent =
                reportDownloadButton.getAttribute("data-loading-label") || originalLabel;
        }

        if (reportDownloadError) {
            reportDownloadError.textContent = "";
            reportDownloadError.style.display = "none";
        }

        fetch(
            "plugins/SquashTmPlugin/jsp/export/doSquashTmExportProject.jsp?projectName="
            + encodeURIComponent(reportDownloadProject),
            {
                method: "GET",
                cache: "no-store",
                credentials: "same-origin"
            }
        )
            .then(function(response) {

                if (!response.ok) {
                    return response.text().then(function(text) {
                        var httpError = new Error(text || "");
                        httpError.status = response.status;
                        httpError.body = text || "";
                        throw httpError;
                    });
                }
                var fileName = getDownloadFileName(
                    response.headers.get("Content-Disposition"),
                    "squashtm-export.xlsx"
                );

                return response.blob().then(function(blob) {
                    return { blob: blob, fileName: fileName };
                });
            })
            .then(function(result) {

                var url = window.URL.createObjectURL(result.blob);
                var link = document.createElement("a");

                link.href = url;
                link.download = result.fileName;
                link.style.display = "none";

                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);

                window.setTimeout(function() {
                    window.URL.revokeObjectURL(url);
                }, 1000);
            })

            .catch(function(error) {

                // Détail complet dans la console du navigateur (F12).
                if (window.console && console.error) {
                    console.error("[SquashTM][EXPORT]", error && error.status, error && error.body, error);
                }

                if (!reportDownloadError) {
                    return;
                }

                var prefix = reportDownloadButton.getAttribute("data-error-label") || "";
                var detail = error && error.message ? error.message : "";

                // Page HTML ou texte trop long : seul le code HTTP est affiché.
                if (detail.length > 300 || detail.indexOf("<") !== -1) {
                    detail = "";
                }

                if (error && error.status) {
                    detail = "(HTTP " + error.status + ")" + (detail ? " " + detail : "");
                }

                reportDownloadError.textContent = detail ? prefix + " " + detail : prefix;
                reportDownloadError.style.display = "block";
            })

            .finally(function() {

                downloading = false;
                reportDownloadButton.disabled = false;

                if (reportDownloadLabel) {
                    reportDownloadLabel.textContent = originalLabel;
                }
            });
    }

    if (reportDownloadButton) {
        reportDownloadButton.addEventListener("click", downloadProjectModule);
    }
    // =============== RÉCUPÉRATION DE LA PROGRESSION ==============

    /**
     * Interroge le serveur pour récupérer l'état de l'importation.
     *
     * @param {string} jobId identifiant du travail d'importation
     */
    function pollImportProgress(jobId) {

        if (!jobId || polling) {
            return;
        }

        polling = true;

        if (progressContainer) {
            progressContainer.style.display = "block";
        }

        if (progressResult) {
            progressResult.style.display = "none";
        }

        fetch("plugins/SquashTmPlugin/jsp/importProgress/importProgress.jsp?jobId=" + encodeURIComponent(jobId), {
            method: "GET",
            cache: "no-store",
            credentials: "same-origin"
        }
        )
            .then(function(response) {

                if (!response.ok) {
                    var error = new Error("Impossible de récupérer la progression (HTTP " + response.status + ").");
                    error.status = response.status;
                    throw error;
                }

                return response.json();
            })

            .then(function(data) {

                updateImportProgress(data);

                if (data.completed) {
                    showImportReport(data);
                    // L'importation est terminée : arrêter le suivi.
                    polling = false;
                    return;
                }

                // Recommencer après une seconde.
                polling = false;

                pollingTimer = window.setTimeout(function() {
                    pollImportProgress(jobId);
                }, 1000);

            })
            .catch(function(error) {
                if (error.status === 400
                    || error.status === 403
                    || error.status === 404) {

                    if (progressMessage) {
                        progressMessage.textContent = error.message;
                    }

                    return;
                }

                polling = false;

                if (progressMessage) {
                    progressMessage.textContent = error.message;
                }

                // Réessayer après 3 secondes en cas d'erreur réseau.
                pollingTimer = window.setTimeout(function() {
                    pollImportProgress(jobId);
                }, 3000);
            });
    }

    // =============== INITIALISATION AUTOMATIQUE ==============

    if (!importForm) {
        return;
    }

    var jobId = importForm.getAttribute("data-import-job-id");

    if (jobId && jobId.trim() !== "") {
        pollImportProgress(jobId);
    }

});