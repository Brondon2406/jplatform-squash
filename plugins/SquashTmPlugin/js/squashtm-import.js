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
                || (cancelAnalysisAction
                    && cancelAnalysisAction.value === "true")) {
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

            // Récupérer les opérations sélectionnées.
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




    // ==========================================================
    // FILTRES DE LA LISTE DE COMPARAISON
    // ==========================================================

    !function($) {
        $(document).ready(function($) {

            // Évite les boucles lorsque le code modifie lui-même un interrupteur.
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

            // ------------------------------------------------------
            // Interrupteurs Jalios (radios Oui/Non ou case à cocher)
            // ------------------------------------------------------

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

            // « Tout sélectionner » reflète l'état des éléments affichés.
            function updateSelectAllState() {

                var $boxes = getVisibleRows().find(".squashtm-comparison-select");
                var allChecked = $boxes.length > 0
                    && $boxes.filter(":checked").length === $boxes.length;

                setSwitch("squashTmSelectAll", allChecked);
                setSwitchDisabled("squashTmSelectAll", $boxes.length === 0);
            }

            // ------------------------------------------------------
            // Filtrage
            // ------------------------------------------------------

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

            // ------------------------------------------------------
            // Menu déroulant : même largeur que la liste, plus compact
            // (compatible Select2 v3 et v4)
            // ------------------------------------------------------

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

                    // Laisse Select2 afficher son menu avant de l'ajuster.
                    setTimeout(function() {
                        fitDropdown(select);
                    }, 0);
                });

            // ------------------------------------------------------
            // Événements
            // ------------------------------------------------------

            // Interrupteur « Filtres » : affiche / masque le panneau.
			$(document).on("change", ".squashtm-filter-toggle", function() {

			    if (syncing) {
			        return;
			    }

			    var on = readSwitch("squashTmFilterToggle");

			    // Afficher ou masquer uniquement les deux filtres.
			    getPanel().css("display", on ? "flex" : "none");

			    if (!on) {
			        resetFilters();
			        applyFilters();
			    }

			});
            // Filtres « Données » et « État ».
            $(document).on("change", ".squashtm-filter-type, .squashtm-filter-status", function() {

                if (syncing) {
                    return;
                }

                applyFilters();
            });

            // « Tout sélectionner » : coche / décoche uniquement les éléments affichés.
            $(document).on("change", ".squashtm-filter-select-all", function() {

                if (syncing) {
                    return;
                }

                var on = readSwitch("squashTmSelectAll");

                getVisibleRows().find(".squashtm-comparison-select").prop("checked", on);
            });

            // Une case cochée à la main met à jour « Tout sélectionner ».
            $(document).on("change", ".squashtm-comparison-select", function() {
                updateSelectAllState();
            });

            // État initial.
            if (getPanel().length) {
                applyFilters();
            }
        });
    }(window.jQuery);


});