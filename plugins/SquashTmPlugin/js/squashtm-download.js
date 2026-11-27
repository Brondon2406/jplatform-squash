/**
 * Téléchargement de la version à jour d'un projet Squash TM (fichier .xlsx).
 * Code partagé par le rapport d'import et la vue "Export Projets".
 *
 * Usage :
 *   SquashTmDownload.downloadProject({
 *       projectName: "MonProjet",
 *       button: element,        // bouton désactivé pendant le téléchargement
 *       labelElement: element,  // (optionnel) texte du bouton, remplacé par loadingLabel
 *       errorElement: element,  // (optionnel) zone d'affichage de l'erreur
 *       loadingLabel: "...",    // (optionnel)
 *       errorLabel: "..."       // (optionnel) préfixe du message d'erreur
 *   });
 */
(function(window) {

    var downloading = false;

    function getDownloadFileName(contentDisposition, fallbackName) {

        if (!contentDisposition) {
            return fallbackName;
        }

        var utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition);

        if (utf8Match && utf8Match[1]) {
            try {
                return decodeURIComponent(utf8Match[1]);
            } catch (e) {
            }
        }

        var plainMatch = /filename="?([^";]+)"?/i.exec(contentDisposition);

        return plainMatch && plainMatch[1] ? plainMatch[1] : fallbackName;
    }

    function downloadProject(options) {

        var projectName = options && options.projectName ? String(options.projectName).trim() : "";
        var button = options.button;
        var labelElement = options.labelElement;
        var errorElement = options.errorElement;

        if (downloading || projectName === "") {
            return;
        }

        downloading = true;

        var originalLabel = labelElement ? labelElement.textContent : "";

        if (button) {
            button.disabled = true;
        }

        if (labelElement) {
            labelElement.textContent = options.loadingLabel || originalLabel;
        }

        if (errorElement) {
            errorElement.textContent = "";
            errorElement.style.display = "none";
        }

        fetch(
            "plugins/SquashTmPlugin/jsp/export/doSquashTmExportProject.jsp?projectName="
            + encodeURIComponent(projectName),
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

                if (!errorElement) {
                    return;
                }

                var prefix = options.errorLabel || "";
                var detail = error && error.message ? error.message : "";

                // Page HTML ou texte trop long : seul le code HTTP est affiché.
                if (detail.length > 300 || detail.indexOf("<") !== -1) {
                    detail = "";
                }

                if (error && error.status) {
                    detail = "(HTTP " + error.status + ")" + (detail ? " " + detail : "");
                }

                errorElement.textContent = detail ? prefix + " " + detail : prefix;
                errorElement.style.display = "block";
            })
            .finally(function() {

                downloading = false;

                if (button) {
                    button.disabled = false;
                }

                if (labelElement) {
                    labelElement.textContent = originalLabel;
                }
            });
    }

    window.SquashTmDownload = {
        downloadProject: downloadProject
    };

})(window);
