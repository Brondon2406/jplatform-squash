document.addEventListener("DOMContentLoaded", function() {
    var fileInput = document.getElementById("squashTmExcelFile");
    var dropZone = document.getElementById("squashTmDropZone");
    var fileError = document.getElementById("squashTmFileError");
    var selectedFile = document.getElementById("squashTmSelectedFile");
    var fileName = document.getElementById("squashTmFileName");
    var fileSize = document.getElementById("squashTmFileSize");
    var projectContainer = document.getElementById("squashTmProjectContainer");
    var projectName = document.getElementById("squashTmProjectName");
    var information = document.getElementById("squashTmImportInformation");
    var action = document.getElementById("squashtmImportAction");
    var importButton = document.getElementById("squashTmImportButton");
    var cancelButton = document.getElementById("squashTmCancelButton");
    var selectedFileObject = null;

    /*   
     * ============== SELECTION DU FICHIER =================
     */

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

        var fileName = file.name.toLowerCase();

        return fileName.endsWith(".xlsx")
            || fileName.endsWith(".xls")
            || fileName.endsWith(".csv");
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
        importButton.disabled = true;
    }

    /*============  BOUTON IMPORTER ==================

    importButton.addEventListener("click", function() {
        if (importButton.disabled) {
            return;
        }
        console.log("Fichier sélectionné :", fileInput.files[0].name);
        console.log("Projet :", projectName.value);

    }); */

});
