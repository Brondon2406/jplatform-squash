
document.addEventListener("DOMContentLoaded", function() {

    // ==========================================================
    // ELEMENTS DU FORMULAIRE D'ANALYSE
    // ==========================================================

    var fileInput = document.getElementById("squashTmExcelFile");
    var dropZone = document.getElementById("squashTmDropZone");
    var fileError = document.getElementById("squashTmFileError");
    var selectedFile = document.getElementById("squashTmSelectedFile");
    var fileName = document.getElementById("squashTmFileName");
    var fileSize = document.getElementById("squashTmFileSize");
    var projectContainer = document.getElementById("squashTmProjectContainer");
    var projectName = document.getElementById("squashTmProjectName");

    var importHeader = document.querySelector(".squashtm-import-header");
    var importPanel = document.querySelector(".squashtm-import-panel");

    var importForm = document.getElementById("squashTmImportForm");
    var information = document.getElementById("squashTmImportInformation");
    var action = document.getElementById("squashtmImportAction");
    var analyzeButton = document.getElementById("squashTmImportButton");
    var cancelButton = document.getElementById("squashTmCancelButton");

    // Actions envoyées au handler Java.
    var analyzeAction = document.getElementById("opAnalyze");
    var importAction = document.getElementById("opImport");
    var cancelAnalysisAction = document.getElementById("opCancelAnalysis");
    var analysisIdInput = document.getElementById("analysisId");

    // ==========================================================
    // ELEMENTS DE LA SYNTHESE
    // ==========================================================

    var analysisContainer = document.getElementById("squashTmAnalysisContainer");
    var confirmImportButton = document.getElementById("squashTmConfirmImportButton");
    var cancelAnalysisButton = document.getElementById("squashTmCancelAnalysisButton");

    var selectedFileObject = null;

    // ==========================================================
    // VERIFICATION DES ELEMENTS DU FORMULAIRE
    // ==========================================================

    if (fileInput && dropZone && importForm) {

        // Sélection du fichier.
        fileInput.addEventListener("change", function() {

            if (fileInput.files.length === 0) {
                return;
            }

            var file = fileInput.files[0];

            if (!isExcelFile(file)) {
                showFileError();
                fileInput.value = "";
                selectedFileObject = null;
                return;
            }

            hideFileError();
            selectedFileObject = file;
            displaySelectedFile(file);
        });

        // Glisser-déposer.
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

            if (!files || files.length === 0) {
                return;
            }

            var file = files[0];

            if (!isExcelFile(file)) {
                showFileError();
                return;
            }

            hideFileError();

            try {
                fileInput.files = files;
            } catch (error) {
                console.error("Impossible d'associer le fichier déposé :", error);
                return;
            }

            selectedFileObject = file;
            displaySelectedFile(file);
        });
    }

    // ==========================================================
    // AFFICHAGE DU FICHIER SELECTIONNE
    // ==========================================================

    function displaySelectedFile(file) {

        if (fileName) {
            fileName.textContent = file.name;
        }

        if (fileSize) {
            fileSize.textContent = formatFileSize(file.size);
        }

        if (dropZone) {
            dropZone.style.display = "none";
        }

        if (selectedFile) {
            selectedFile.style.display = "block";
        }

        if (projectContainer) {
            projectContainer.style.display = "block";
        }

        if (information) {
            information.style.display = "block";
        }

        if (action) {
            action.style.display = "flex";
        }

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

        return (kilobytes / 1024).toFixed(1) + " Mo";
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

    function showFileError() {
        if (fileError) {
            fileError.style.display = "block";
        }
    }

    function hideFileError() {
        if (fileError) {
            fileError.style.display = "none";
        }
    }

    // ==========================================================
    // VALIDATION DU FORMULAIRE
    // ==========================================================

    function validateForm() {

        var hasFile = fileInput
            && fileInput.files.length > 0
            && isExcelFile(fileInput.files[0]);

        var hasProjectName = projectName
            && projectName.value.trim().length > 0;

        if (analyzeButton) {
            analyzeButton.disabled = !(hasFile && hasProjectName);
        }
    }

    if (projectName) {
        projectName.addEventListener("input", validateForm);
    }

    // ==========================================================
    // SOUMISSION POUR ANALYSE
    // ==========================================================

    if (importForm) {
        importForm.addEventListener("submit", function(event) {

            // La synthèse possède ses propres actions.
            if (analysisContainer
                && analysisContainer.dataset.analysisReady === "true") {
                return;
            }

            if (!fileInput || !fileInput.files.length
                || !isExcelFile(fileInput.files[0])) {

                event.preventDefault();
                showFileError();
                return;
            }

            if (!projectName || !projectName.value.trim()) {
                event.preventDefault();

                if (projectName) {
                    projectName.focus();
                }

                return;
            }

            // Demander une analyse au serveur, pas un import direct.
            if (analyzeAction) {
                analyzeAction.value = "true";
            }

            if (importAction) {
                importAction.value = "false";
            }

            if (cancelAnalysisAction) {
                cancelAnalysisAction.value = "false";
            }

            // Ne pas appeler importForm.submit() ici :
            // le navigateur doit envoyer normalement le formulaire.
        });
    }

    // ==========================================================
    // ANNULATION ET REMISE A ZERO DU FORMULAIRE
    // ==========================================================

    function resetImportForm() {

        if (fileInput) {
            fileInput.value = "";
        }

        selectedFileObject = null;

        if (projectName) projectName.value = "";
        if (fileName) fileName.textContent = "";
        if (fileSize) fileSize.textContent = "";

        if (selectedFile) selectedFile.style.display = "none";
        if (projectContainer) projectContainer.style.display = "none";
        if (information) information.style.display = "none";
        if (action) action.style.display = "none";
        if (dropZone) {
            dropZone.style.display = "flex";
            dropZone.classList.remove("dragover");
        }

        hideFileError();

        if (analyzeButton) {
            analyzeButton.disabled = true;
        }

        if (analyzeAction) analyzeAction.value = "false";
        if (importAction) importAction.value = "false";
        if (cancelAnalysisAction) cancelAnalysisAction.value = "false";
        if (analysisIdInput) analysisIdInput.value = "";
    }

    if (cancelButton) {
        cancelButton.addEventListener("click", function() {
            resetImportForm();
        });
    }

    // ==========================================================
    // AFFICHAGE DE LA SYNTHESE D'ANALYSE
    // ==========================================================

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

        // La synthèse est affichée uniquement si le serveur
        // confirme qu'une analyse est disponible.
        analysisContainer.style.display = "block";
    }

    if (analysisContainer
        && analysisContainer.dataset.analysisReady === "true") {
        showAnalysisSummary();
    }

    // ==========================================================
    // ACTIONS DE LA SYNTHESE
    // ==========================================================

    if (confirmImportButton) {
        confirmImportButton.addEventListener("click", function() {

            if (importAction) {
                importAction.value = "true";
            }

            if (analyzeAction) {
                analyzeAction.value = "false";
            }

            if (cancelAnalysisAction) {
                cancelAnalysisAction.value = "false";
            }

            // La JSP doit soumettre le formulaire de confirmation
            // contenant l'identifiant d'analyse.
            if (importForm) {
                importForm.submit();
            } else {
                console.error("Formulaire d'importation introuvable.");
            }
        });
    }

    if (cancelAnalysisButton) {
        cancelAnalysisButton.addEventListener("click", function() {

            if (cancelAnalysisAction) {
                cancelAnalysisAction.value = "true";
            }

            if (analyzeAction) {
                analyzeAction.value = "false";
            }

            if (importAction) {
                importAction.value = "false";
            }

            if (analysisForm) {
                analysisForm.submit();
            } else if (importForm) {
                importForm.submit();
            }
        });
    }

});
