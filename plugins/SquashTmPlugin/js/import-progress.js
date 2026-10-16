document.addEventListener("DOMContentLoaded", function () {

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
                    ? "squashtm-progress-result success"
                    : "squashtm-progress-result error";
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
        .then(function (response) {

            if (!response.ok) {
                throw new Error(
                    "Impossible de récupérer la progression (HTTP "
                    + response.status + ")."
                );
            }

            return response.json();
        })
        .then(function (data) {

            updateImportProgress(data);

            if (data.completed) {
                // L'importation est terminée : arrêter le suivi.
                polling = false;
                return;
            }

            // Recommencer après une seconde.
            polling = false;

            pollingTimer = window.setTimeout(function () {
                pollImportProgress(jobId);
            }, 1000);

        })
        .catch(function (error) {

            polling = false;

            if (progressMessage) {
                progressMessage.textContent = error.message;
            }

            // Réessayer après 3 secondes en cas d'erreur réseau.
            pollingTimer = window.setTimeout(function () {
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