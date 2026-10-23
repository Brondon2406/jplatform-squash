document.addEventListener("DOMContentLoaded", function() {
    var fileInput = document.getElementById("squashTmExcelFile");
    var dropZone = document.getElementById("squashTmDropZone");
    var templateDownload = document.getElementById("squashTmTemplateDownload");
    var fileError = document.getElementById("squashTmFileError");
    var selectedFile = document.getElementById("squashTmSelectedFile");
    var fileName = document.getElementById("squashTmFileName");
    var fileSize = document.getElementById("squashTmFileSize");
    var projectContainer = document.getElementById("squashTmProjectContainer");
    var projectName = document.getElementById("squashTmProjectName");
    var importHeader = document.querySelector(".squashtm-import-header");
    var importPanel = document.querySelector(".squashtm-import-panel");
    var progressContainer = document.getElementById("squashTmProgressContainer");
    var importForm = document.getElementById("squashTmImportForm");
    var information = document.getElementById("squashTmImportInformation");
    var action = document.getElementById("squashtmImportAction");
    var importButton = document.getElementById("squashTmImportButton");
    var cancelButton = document.getElementById("squashTmCancelButton");
    var selectedFileObject = null;
    // Actions envoyées au SquashTmImportHandler.
    var analyzeAction = document.getElementById("opAnalyze");
    var importAction = document.getElementById("opImport");
    var cancelAnalysisAction = document.getElementById("opCancelAnalysis");
    var analysisIdInput = document.getElementById("analysisId");

    // Synthèse de l'analyse.
    var analysisContainer = document.getElementById("squashTmAnalysisContainer");
    var confirmImportButton = document.getElementById("squashTmConfirmImportButton");
    var cancelAnalysisButton = document.getElementById("squashTmCancelAnalysisButton");

    /*   
     * ============== SELECTION DU FICHIER =================
     */


    if (fileInput) {
        fileInput.addEventListener("change", function() {
            if (fileInput.files.length === 0) {
                return;
            }

            var file = fileInput.files[0];

            if (!isExcelFile(file)) {
                fileError.style.display = "block";
                fileInput.value = "";
                return;
            }

            fileError.style.display = "none";
            selectedFileObject = file;
            displaySelectedFile(selectedFileObject);
        });

    }

    /*
     * ==========================================================
     * GLISSER-DEPOSER
     * ==========================================================
     */

    dropZone.addEventListener("dragover", function(event) {
        event.preventDefault();
        dropZone.classList.add("dragover");
    });

    dropZone.addEventListener("dragleave", function(event) {
        event.preventDefault();
        dropZone.classList.remove("dragover");
    });

    dropZone.addEventListener("drop", function(event) {
        event.preventDefault();
        dropZone.classList.remove("dragover");

        var files = event.dataTransfer.files;

        if (files.length === 0) {
            return;
        }

        var file = files[0];

        if (!isExcelFile(file)) {
            fileError.style.display = "block";
            return;
        }

        fileError.style.display = "none";
        selectedFileObject = file;
        fileInput.files = files;

        displaySelectedFile(selectedFileObject);
    });


    /*
     * ===========  AFFICHAGE DU FICHIER SELECTIONNE ============
     */

    function displaySelectedFile(file) {

        fileName.textContent = file.name;
        fileSize.textContent = formatFileSize(file.size);
        dropZone.style.display = "none";
        selectedFile.style.display = "block";
		
        if (templateDownload) {
            templateDownload.style.display = "none";
        }
		
        projectContainer.style.display = "block";
        information.style.display = "block";
        action.style.display = "flex";
        validateForm();
    }

    function formatFileSize(bytes) {

        if (bytes < 1024) {
            return bytes + " octets";
        }

        var kilobytes = bytes / 1024;

        if (kilobytes < 1024) {
            return kilobytes.toFixed(1) + " Ko";
        }

        var megabytes = kilobytes / 1024;

        return megabytes.toFixed(1) + " Mo";
    }

    function isExcelFile(file) {
        if (!file || !file.name) {
            return false;
        }

        var name = file.name.toLowerCase();

        return name.endsWith(".xlsx")
            || name.endsWith(".xls")
            || name.endsWith(".csv");
    }

    /*
     * ============== SOUMISSION POUR ANALYSE ==============
     */

    if (importForm) {
        importForm.addEventListener("submit", function(event) {

            // Une action de confirmation ou d'annulation
            // a déjà été préparée par son bouton.
            if ((importAction && importAction.value === "true")
                || (cancelAnalysisAction
                    && cancelAnalysisAction.value === "true")) {
                return;
            }

            // Le premier clic sur Importer lance uniquement l'analyse.
            if (!fileInput
                || !fileInput.files.length
                || !isExcelFile(fileInput.files[0])) {

                event.preventDefault();

                if (fileError) {
                    fileError.style.display = "block";
                }

                return;
            }

            if (!projectName || !projectName.value.trim()) {
                event.preventDefault();

                if (projectName) {
                    projectName.focus();
                }

                return;
            }

            if (analyzeAction) {
                analyzeAction.value = "true";
            }

            if (importAction) {
                importAction.value = "false";
            }

            if (cancelAnalysisAction) {
                cancelAnalysisAction.value = "false";
            }
        });
    }

    /*
     * ============== AFFICHAGE DE LA SYNTHESE ==============
     */

    function showAnalysisSummary() {
        if (!analysisContainer) {
            return;
        }

        if (importHeader) {
            importHeader.style.display = "none";
        }

        if (importPanel) {
            importPanel.style.display = "none";
        }

        if (progressContainer) {
            progressContainer.style.display = "none";
        }

        analysisContainer.style.display = "block";

        // Relancer l'animation à chaque affichage de la synthèse.
        analysisContainer.classList.remove("analysis-animated");
        void analysisContainer.offsetWidth;
        analysisContainer.classList.add("analysis-animated");
    }

    if (importForm && importForm.dataset.analysisReady === "true") {
        showAnalysisSummary();
    }

    /*
     * ============== CONFIRMER L'IMPORTATION ==============
     */

    if (confirmImportButton) {
        confirmImportButton.addEventListener("click", function() {

            if (!analysisIdInput || !analysisIdInput.value) {
                console.error("Identifiant de l'analyse introuvable.");
                return;
            }

            if (importAction) {
                importAction.value = "true";
            }

            if (analyzeAction) {
                analyzeAction.value = "false";
            }

            if (cancelAnalysisAction) {
                cancelAnalysisAction.value = "false";
            }
        });
    }

    /*
     * ============== ANNULER L'ANALYSE ==============
     */


    if (cancelAnalysisButton) {
        cancelAnalysisButton.addEventListener("click", function(event) {
            event.preventDefault();

            if (cancelAnalysisAction) {
                cancelAnalysisAction.value = "true";
            }

            if (analyzeAction) {
                analyzeAction.value = "false";
            }

            if (importAction) {
                importAction.value = "false";
            }

            importForm.submit();
        });
    }


    /* 
     * ================ ANNULER ==================
     */

    cancelButton.addEventListener("click", function() {
        resetImportForm();

    });

    /* 
     * =================  MODIFICATION DU NOM DU PROJET =========================
     */

    projectName.addEventListener("input", function() {
        validateForm();

    });

    /*  
     * ==========  VALIDATION DU FORMULAIRE ====================
     */

    function validateForm() {

        var hasFile = fileInput.files.length > 0;
        var hasProjectName = projectName.value.trim().length > 0;

        importButton.disabled = !(hasFile && hasProjectName);

    }

    /*   
     * ===============  REMISE À L'ÉTAT INITIAL =========================
     */

    function resetImportForm() {

        fileInput.value = "";
        selectedFileObject = null;
        projectName.value = "";
        fileName.textContent = "";
        fileSize.textContent = "";
        selectedFile.style.display = "none";
        projectContainer.style.display = "none";
        information.style.display = "none";
        action.style.display = "none";
        dropZone.style.display = "flex";
		
		// Réafficher le téléchargement du modèle
		if (templateDownload) {
		    templateDownload.style.display = "flex";
		}
		
        fileError.style.display = "none";
        dropZone.classList.remove("dragover");
        importButton.disabled = true;
    }

    /*
     * ============== INITIALISATION DE L'AFFICHAGE ==============
     */

    if (importForm) {
        if (importForm.dataset.importJobId) {
            showImportProgressOnly();
        } else if (importForm.dataset.analysisReady === "true") {
            showAnalysisSummary();
        } else {
            // Aucun import en cours ni analyse en attente :
            // afficher le formulaire initial.
            if (importHeader) {
                importHeader.style.display = "";
            }

            if (importPanel) {
                importPanel.style.display = "";
            }

            if (progressContainer) {
                progressContainer.style.display = "none";
            }

            if (analysisContainer) {
                analysisContainer.style.display = "none";
            }
        }
    }

    /*
     * ============== AFFICHAGE DU SUIVI DE L'IMPORTATION ==============
     */

    function showImportProgressOnly() {

        // Masquer le titre et le formulaire de sélection
        importHeader.style.display = "none";
        importPanel.style.display = "none";

        // Afficher uniquement le suivi de l'importation
        progressContainer.style.display = "block";
    }

    // ============== BOUTON TERMINER ==============
    var finishButton = document.getElementById("squashTmReportFinishButton");
    var importReport = document.getElementById("squashTmImportReport");

    if (finishButton) {
        finishButton.addEventListener("click", function() {

            // Masquer le rapport final
            if (importReport) {
                importReport.style.display = "none";
            }

            // Réinitialiser le formulaire
            resetImportForm();

            // Masquer la progression
            if (progressContainer) {
                progressContainer.style.display = "none";
            }

            // Masquer la synthèse de l'analyse
            if (analysisContainer) {
                analysisContainer.style.display = "none";
            }

            // Réafficher le formulaire initial
            if (importHeader) {
                importHeader.style.display = "";
            }

            if (importPanel) {
                importPanel.style.display = "";
            }
            window.location.replace(window.location.href);
        });
    }

});