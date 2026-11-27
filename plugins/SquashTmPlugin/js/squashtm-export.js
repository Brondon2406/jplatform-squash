

(function() {

    document.addEventListener("click", function(event) {

        var target = event.target;
        var button = target && target.closest ? target.closest(".squashtm-export-download-btn") : null;

        if (!button) {
            return;
        }

        event.preventDefault();

        var errorBox = document.getElementById("squashTmExportDownloadError");

        SquashTmDownload.downloadProject({
            projectName: button.getAttribute("data-project-name"),
            button: button,
            labelElement: button.querySelector(".squashtm-export-download-label"),
            errorElement: errorBox,
            loadingLabel: errorBox ? errorBox.getAttribute("data-loading-label") : "",
            errorLabel: errorBox ? errorBox.getAttribute("data-error-label") : ""
        });
    });
})();


!function($) {

    if (!$) {
        return;
    }

    var SEARCH_INPUT = ".squashtm-export-navbar input[name='searchTerm']";

    function refreshExportView(searchTerm) {

        var view = $("input[name='hiddenExportView']").val() || "EXPORT_FILES";
        var params = {
            "view": view
        };

        if (searchTerm) {
            params["searchTerm"] = searchTerm;
        }

        $(".squashtm-export-app-body").displayLoading();
        $(".squashtm-export-app-body").refresh({
            nohistory: true,
            noscroll: true,
            params: params
        });
    }

    $(document).on("click", ".squashtm-export-search-btn", function(event) {
        event.preventDefault();
        refreshExportView($.trim($(SEARCH_INPUT).val() || ""));
    });

    $(document).on("click", ".squashtm-export-reset-btn", function(event) {
        event.preventDefault();
        refreshExportView("");
    });

    $(document).on("keydown", SEARCH_INPUT, function(event) {
        if (event.which === 13) {
            event.preventDefault();
            refreshExportView($.trim($(this).val() || ""));
        }
    });

}(window.jQuery);
