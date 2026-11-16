/*
 * Vue Administration du plugin Squash TM : mise à jour du template Excel et de
 * son mapping.
 *
 * Les écouteurs sont posés sur le document (délégation d'événements) : ils
 * restent valables même si la vue est rechargée en Ajax.
 */
(function() {
    "use strict";

    var FIELD_SELECTOR = "select[name=\"columnFields\"]";

    /**
     * Vérifie que le fichier choisi est un classeur Excel.
     */
    function isExcelFile(file) {
        var name = (file && file.name ? file.name : "").toLowerCase();
        return name.endsWith(".xlsx") || name.endsWith(".xls");
    }

    /**
     * Fichier de template sélectionné : affichage du nom et activation du bouton.
     */
    function onTemplateFileSelected(input) {
        var fileName = document.getElementById("squashTmTemplateFileName");
        var fileError = document.getElementById("squashTmTemplateFileError");
        var analyzeButton = document.getElementById("squashTmTemplateAnalyzeButton");

        var file = input.files && input.files.length > 0 ? input.files[0] : null;

        if (!file) {
            return;
        }

        if (!isExcelFile(file)) {
            input.value = "";
            if (fileName) {
                fileName.textContent = "";
            }
            if (fileError) {
                fileError.style.display = "block";
            }
            if (analyzeButton) {
                analyzeButton.disabled = true;
            }
            return;
        }

        if (fileError) {
            fileError.style.display = "none";
        }
        if (fileName) {
            fileName.textContent = file.name;
        }
        if (analyzeButton) {
            analyzeButton.disabled = false;
        }
    }

    /**
     * Un même élément ne peut correspondre qu'à une seule colonne : si
     * l'administrateur le choisit pour une deuxième colonne, la première est
     * remise à "Colonne non utilisée".
     */
    function preventDuplicateSelection(changedSelect) {
        if (!changedSelect.value) {
            return;
        }

        var form = changedSelect.form;
        if (!form) {
            return;
        }

        var selects = form.querySelectorAll(FIELD_SELECTOR);

        for (var i = 0;i < selects.length;i++) {
            var other = selects[i];
            if (other !== changedSelect && other.value === changedSelect.value) {
                other.value = "";
                highlight(other);
            }
        }
    }

    function highlight(select) {
        select.classList.add("squashtm-mapping-field-reset");
        window.setTimeout(function() {
            select.classList.remove("squashtm-mapping-field-reset");
        }, 1500);
    }

    document.addEventListener("change", function(event) {
        var target = event.target;

        if (!target) {
            return;
        }

        if (target.id === "squashTmTemplateFile") {
            onTemplateFileSelected(target);
            return;
        }

        if (target.matches && target.matches(FIELD_SELECTOR)) {
            preventDuplicateSelection(target);
        }
    });

    /*
     * Bouton "Analyser" désactivé tant qu'aucun fichier n'est choisi.
     * (La confirmation du bouton de réinitialisation est gérée par jalios:button.)
     */
    function initAnalyzeButton() {
        var analyzeButton = document.getElementById("squashTmTemplateAnalyzeButton");
        var input = document.getElementById("squashTmTemplateFile");

        if (analyzeButton && input && (!input.files || input.files.length === 0)) {
            analyzeButton.disabled = true;
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initAnalyzeButton);
    } else {
        initAnalyzeButton();
    }
})();