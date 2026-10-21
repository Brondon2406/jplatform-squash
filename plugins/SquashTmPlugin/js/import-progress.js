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

    // Éviter plusieurs interrogations simultanées.
    var polling = false;

    // Identifiant du minuteur de suivi.
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
            Math.min(100, Number(data.percentage) || 0)
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

        // Masquer la progression et afficher le rapport.
        if (progressContainer) {
            progressContainer.style.display = "none";
        }

        importReport.style.display = "block";
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

        fetch(
            "plugins/SquashTmPlugin/jsp/importProgress/importProgress.jsp?jobId="
            + encodeURIComponent(jobId),
            {
                method: "GET",
                cache: "no-store",
                credentials: "same-origin"
            }
        )
            .then(function(response) {

                if (!response.ok) {
                    var error = new Error(
                        "Impossible de récupérer la progression (HTTP "
                        + response.status + ")."
                    );

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