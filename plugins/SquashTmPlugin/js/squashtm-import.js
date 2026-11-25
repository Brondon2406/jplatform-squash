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

    var analyzeAction = document.getElementById("opAnalyze");
    var importAction = document.getElementById("opImport");
    var cancelAnalysisAction = document.getElementById("opCancelAnalysis");
    var analysisIdInput = document.getElementById("analysisId");
    var selectedComparisonIndexesInput = document.getElementById("selectedComparisonIndexes");

    var analysisContainer = document.getElementById("squashTmAnalysisContainer");
    var confirmImportButton = document.getElementById("squashTmConfirmImportButton");
    var cancelAnalysisButton = document.getElementById("squashTmCancelAnalysisButton");
    var loadAnalysisAction = document.getElementById("opLoadAnalysis");

    var progressBar = document.getElementById("squashTmProgressBar");
    var progressPercentage = document.getElementById("squashTmProgressPercentage");
    var progressMessage = document.getElementById("squashTmProgressMessage");
    var progressStage = document.getElementById("squashTmProgressStage");
    var progressElapsed = document.getElementById("squashTmProgressElapsed");
    var progressResult = document.getElementById("squashTmProgressResult");
    var progressTitleText = document.getElementById("squashTmProgressTitleText");

    if (!importForm || !fileInput || !dropZone || !projectName) {
        return;
    }

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

            
            if ((importAction && importAction.value === "true")
                || (cancelAnalysisAction && cancelAnalysisAction.value === "true")
                || (loadAnalysisAction && loadAnalysisAction.value === "true")) {
                return;
            }

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

            event.preventDefault();

            if (analysisRunning) {
                return;
            }

            startAnalysis();
        });
    }

    /*
     * ============== ANALYSE EN ARRIERE-PLAN + BARRE DE PROGRESSION ==============
     */

    var analysisRunning = false;
    var analysisTimer = null;
    var elapsedTimer = null;
    var analysisStartedAt = 0;

    var ANALYSIS_STAGES = {
        PREPARATION: "Préparation",
        ANALYSE_FICHIER: "Analyse du fichier",
        COMPARAISON: "Correspondance avec Squash TM",
        SYNTHESE: "Préparation de la synthèse",
        TERMINE: "Terminé",
        TERMINE_AVEC_ERREURS: "Terminé avec erreurs"
    };

    function setAnalysisBusy(busy) {
        analysisRunning = busy;

        if (cancelButton) {
            cancelButton.disabled = busy;
        }

        if (busy) {
            importButton.disabled = true;
        } else {
            validateForm();
        }
    }

    function renderAnalysisProgress(percentage, stage, message) {

        var value = Math.max(0, Math.min(100, Number(percentage) || 0));

        if (progressBar) {
            progressBar.style.width = value + "%";
            progressBar.setAttribute("aria-valuenow", value);
        }

        if (progressPercentage) {
            progressPercentage.textContent = value + " %";
        }

        if (progressMessage && message) {
            progressMessage.textContent = message;
        }

        if (progressStage && stage) {
            progressStage.textContent = ANALYSIS_STAGES[stage] || stage;
        }
    }

    function renderElapsed() {
        if (progressElapsed) {
            progressElapsed.textContent = Math.floor((Date.now() - analysisStartedAt) / 1000) + " s";
        }
    }

    function stopAnalysisTimers() {
        window.clearTimeout(analysisTimer);
        window.clearInterval(elapsedTimer);
    }

    function showAnalysisProgress() {

        var previousError = document.getElementById("squashTmAnalysisError");

        if (previousError && previousError.parentNode) {
            previousError.parentNode.removeChild(previousError);
        }

        if (importHeader) {
            importHeader.style.display = "none";
        }

        if (importPanel) {
            importPanel.style.display = "none";
        }

        if (progressContainer) {
            progressContainer.style.display = "block";
        }

        if (progressResult) {
            progressResult.style.display = "none";
        }

        if (progressBar) {
            progressBar.className = "progress-bar progress-bar-striped active";
        }

        if (progressTitleText) {
            progressTitleText.textContent = "Analyse du fichier";
        }

        renderAnalysisProgress(0, "PREPARATION", "Envoi du fichier au serveur...");
        renderElapsed();

        elapsedTimer = window.setInterval(renderElapsed, 1000);
    }

    function startAnalysis() {

        var formData = new FormData(importForm);

        formData.set("opAnalyze", "false");
        formData.set("opAnalyzeAsync", "true");
        formData.set("opImport", "false");
        formData.set("opCancelAnalysis", "false");
        formData.set("opLoadAnalysis", "false");

        analysisStartedAt = Date.now();

        setAnalysisBusy(true);
        showAnalysisProgress();

        var startUrl = (importForm.getAttribute("data-progress-url")
            || "plugins/SquashTmPlugin/jsp/importProgress/importProgress.jsp")
            .replace("importProgress.jsp", "startAnalysis.jsp");

        fetch(startUrl, {
            method: "POST",
            body: formData,
            credentials: "same-origin",
            cache: "no-store"
        })
            .then(function(response) {

                return response.text().then(function(text) {

                    var data = null;

                    try {
                        data = JSON.parse(text);
                    } catch (e) {
                        data = null;
                    }

                    if (!response.ok || !data || !data.jobId) {
                        throw new Error((data && data.error)
                            || "Impossible de démarrer l'analyse (HTTP " + response.status + ").");
                    }

                    return data.jobId;
                });
            })
            .then(function(jobId) {
                renderAnalysisProgress(5, "PREPARATION", "Fichier reçu, préparation de l'analyse...");
                pollAnalysis(jobId);
            })
            .catch(function(error) {
                showAnalysisError(error.message);
            });
    }

    function pollAnalysis(jobId) {

        var progressUrl = importForm.getAttribute("data-progress-url")
            || "plugins/SquashTmPlugin/jsp/importProgress/importProgress.jsp";

        fetch(progressUrl + "?jobId=" + encodeURIComponent(jobId), {
            method: "GET",
            cache: "no-store",
            credentials: "same-origin"
        })
            .then(function(response) {

                if (!response.ok) {
                    var error = new Error("Impossible de récupérer la progression (HTTP " + response.status + ").");
                    error.status = response.status;
                    throw error;
                }

                return response.json();
            })
            .then(function(data) {

                renderAnalysisProgress(data.percentage, data.stage, data.message);

                if (!data.completed) {
                    analysisTimer = window.setTimeout(function() {
                        pollAnalysis(jobId);
                    }, 700);
                    return;
                }

                if (data.success && data.analysisId) {
                    finishAnalysis(data.analysisId);
                } else {
                    showAnalysisError(data.analysisError || data.message);
                }
            })
            .catch(function(error) {

                if (error.status === 400 || error.status === 403 || error.status === 404) {
                    showAnalysisError(error.message);
                    return;
                }

                analysisTimer = window.setTimeout(function() {
                    pollAnalysis(jobId);
                }, 2000);
            });
    }

    function finishAnalysis(analysisId) {

        stopAnalysisTimers();
        renderAnalysisProgress(100, "TERMINE", "Analyse terminée, affichage de la synthèse...");

        if (analysisIdInput) {
            analysisIdInput.value = analysisId;
        }

        if (analyzeAction) {
            analyzeAction.value = "false";
        }

        if (importAction) {
            importAction.value = "false";
        }

        if (cancelAnalysisAction) {
            cancelAnalysisAction.value = "false";
        }

        if (loadAnalysisAction) {
            loadAnalysisAction.value = "true";
        }

        fileInput.value = "";

        importForm.submit();
    }

    function showAnalysisError(message) {

        stopAnalysisTimers();

        if (progressContainer) {
            progressContainer.style.display = "none";
        }

        if (importHeader) {
            importHeader.style.display = "";
        }

        if (importPanel) {
            importPanel.style.display = "";
        }

        var errorBox = document.getElementById("squashTmAnalysisError");

        if (!errorBox) {
            errorBox = document.createElement("div");
            errorBox.id = "squashTmAnalysisError";
            errorBox.className = "alert alert-danger squashtm-import-error";

            if (importPanel && importPanel.parentNode) {
                importPanel.parentNode.insertBefore(errorBox, importPanel);
            }
        }

        errorBox.textContent = message || "L'analyse a échoué.";

        setAnalysisBusy(false);
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

        analysisContainer.classList.remove("analysis-animated");
        void analysisContainer.offsetWidth;
        analysisContainer.classList.add("analysis-animated");
    }

    /*
     * ============== CONFIRMER L'IMPORTATION ==============
     */

    if (confirmImportButton) {
        confirmImportButton.addEventListener("click", function(event) {

            if (!analysisIdInput || !analysisIdInput.value) {
                console.error("Identifiant de l'analyse introuvable.");
                return;
            }

            if (selectedComparisonIndexesInput) {

                var selectedCheckboxes = document.querySelectorAll(
                    ".squashtm-comparison-select:checked"
                );

                var selectedIndexes = [];

                selectedCheckboxes.forEach(function(checkbox) {
                    selectedIndexes.push(checkbox.value);
                });

                selectedComparisonIndexesInput.value = selectedIndexes.join(",");

                var selectionError = document.getElementById("squashTmComparisonSelectionError");
                var hasCheckboxes = document.querySelectorAll(".squashtm-comparison-select").length > 0;

                if (hasCheckboxes) {
                    if (selectedIndexes.length === 0) {
                        event.preventDefault();

                        if (selectionError) {
                            selectionError.style.display = "block";
                        }
                        return;
                    }

                    if (selectionError) {
                        selectionError.style.display = "none";
                    }
                }
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

        if (templateDownload) {
            templateDownload.style.display = "block";
        }

        if (selectedComparisonIndexesInput) {
            selectedComparisonIndexesInput.value = "";
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

        importHeader.style.display = "none";
        importPanel.style.display = "none";

        progressContainer.style.display = "block";
    }

    
    var finishButton = document.getElementById("squashTmReportFinishButton");
    var importReport = document.getElementById("squashTmImportReport");

    if (finishButton) {
        finishButton.addEventListener("click", function() {

            if (importReport) {
                importReport.style.display = "none";
            }
			
            resetImportForm();

            if (progressContainer) {
                progressContainer.style.display = "none";
            }

            if (analysisContainer) {
                analysisContainer.style.display = "none";
            }

            if (importHeader) {
                importHeader.style.display = "";
            }

            if (importPanel) {
                importPanel.style.display = "";
            }
            window.location.replace(window.location.href);
        });
    }


    !function($) {
        $(document).ready(function($) {

            var syncing = false;

            function getPanel() {
                return $("#squashTmFilters");
            }

            function getAllLabel() {
                return $("#squashTmComparisonFilters").attr("data-all-label") || "";
            }

            function getRows() {
                return $(".squashtm-comparison-row");
            }

            function getVisibleRows() {
                return getRows().filter(function() {
                    return this.style.display !== "none";
                });
            }


            function readSwitch(name) {
                var $inputs = $("input[name='" + name + "']");
                var $radios = $inputs.filter("[type='radio']");

                if ($radios.length) {
                    return $radios.filter(":checked").val() === "true";
                }
                return $inputs.filter("[type='checkbox']").is(":checked");
            }

            function setSwitch(name, on) {

                if (readSwitch(name) === on) {
                    return;
                }

                var $inputs = $("input[name='" + name + "']");
                var $radios = $inputs.filter("[type='radio']");

                syncing = true;

                if ($radios.length) {
                    $radios.filter(function() {
                        return (this.value === "true") === on;
                    }).prop("checked", true).trigger("change");
                } else {
                    $inputs.filter("[type='checkbox']").prop("checked", on).trigger("change");
                }

                $(".squashtm-filter-select-all .switch").attr("aria-checked", on ? "true" : "false");

                syncing = false;
            }

            function setSwitchDisabled(name, disabled) {
                $("input[name='" + name + "']").prop("disabled", disabled);
            }

            function updateSelectAllState() {

                var $boxes = getVisibleRows().find(".squashtm-comparison-select");
                var allChecked = $boxes.length > 0
                    && $boxes.filter(":checked").length === $boxes.length;

                setSwitch("squashTmSelectAll", allChecked);
                setSwitchDisabled("squashTmSelectAll", $boxes.length === 0);
            }


            function applyFilters() {

                var allLabel = getAllLabel();
                var type = $("select[name='squashTmFilterType']").val() || allLabel;
                var status = $("select[name='squashTmFilterStatus']").val() || allLabel;
                var visibleCount = 0;

                getRows().each(function() {

                    var $row = $(this);

                    var matches = (type === allLabel || $row.attr("data-type") === type)
                        && (status === allLabel || $row.attr("data-status") === status);

                    $row.css("display", matches ? "" : "none");

                    if (matches) {
                        visibleCount++;
                    }
                });

                $("#squashTmComparisonEmpty").css("display", visibleCount === 0 ? "" : "none");

                updateSelectAllState();
            }

            function resetFilters() {

                var allLabel = getAllLabel();

                syncing = true;
                $("select[name='squashTmFilterType']").val(allLabel).trigger("change");
                $("select[name='squashTmFilterStatus']").val(allLabel).trigger("change");
                syncing = false;
            }


            function fitDropdown(select) {

                var $select = $(select);
                var $container = $select.siblings(".select2-container").first();

                if (!$container.length) {
                    $container = $select.parent().find(".select2-container").first();
                }

                var width = $container.outerWidth();

                if (!width) {
                    return;
                }

                var $dropdown = $(".select2-dropdown:visible, .select2-drop-active:visible").last();

                if (!$dropdown.length) {
                    return;
                }

                $dropdown.addClass("squashtm-filter-dropdown");

                [$dropdown[0], $dropdown.parent(".select2-container")[0]].forEach(function(element) {
                    if (element) {
                        element.style.setProperty("width", width + "px", "important");
                        element.style.setProperty("min-width", width + "px", "important");
                        element.style.setProperty("max-width", width + "px", "important");
                    }
                });
            }

            $(document).on("select2:open select2-open",
                "select[name='squashTmFilterType'], select[name='squashTmFilterStatus']",
                function() {
                    var select = this;

                    setTimeout(function() {
                        fitDropdown(select);
                    }, 0);
                });


            $(document).on("change", ".squashtm-filter-toggle", function() {

                if (syncing) {
                    return;
                }

                var on = readSwitch("squashTmFilterToggle");
                getPanel().css("display", on ? "flex" : "none");

                if (!on) {
                    resetFilters();
                    applyFilters();
                }

            });
            $(document).on("change", ".squashtm-filter-type, .squashtm-filter-status", function() {

                if (syncing) {
                    return;
                }

                applyFilters();
            });

            $(document).on("change", ".squashtm-filter-select-all", function() {

                if (syncing) {
                    return;
                }

                var on = readSwitch("squashTmSelectAll");

                getVisibleRows().find(".squashtm-comparison-select").prop("checked", on);
            });

            $(document).on("change", ".squashtm-comparison-select", function() {
                updateSelectAllState();
            });

            if (getPanel().length) {
                applyFilters();
            }
        });
    }(window.jQuery);


});