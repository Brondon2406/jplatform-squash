/*
 * Dashboard Squash TM : filtres, indicateurs, graphiques et tableaux.
 *
 * Les données sont demandées à dashboardData.jsp (JSON). Aucun composant
 * externe n'est nécessaire : les graphiques sont dessinés en HTML / SVG.
 */
(function() {
    "use strict";

    var STORAGE_KEY = "squashtm.dashboard.filters.v1";

    var PALETTE = ["#33446B", "#4F7CAC", "#7FA7C9", "#6BA583", "#E0A458", "#C8553D", "#8E6C8A", "#6B7B8C"];

    
    var VALUE_COLORS = {
        UNDEFINED: "#A0A8B5",
        APPROVED: "#3E8E5A", SUCCESS: "#3E8E5A", SETTLED: "#6BA583", FINISHED: "#3E8E5A",
        WORK_IN_PROGRESS: "#4F7CAC", IN_PROGRESS: "#4F7CAC", RUNNING: "#4F7CAC", PLANNED: "#7FA7C9",
        UNDER_REVIEW: "#E0A458", WARNING: "#E0A458", BLOCKED: "#D98E04",
        TO_BE_UPDATED: "#C8553D", FAILURE: "#C0392B", ERROR: "#C0392B", MAJOR: "#C8553D",
        CRITICAL: "#8E2B2B", MINOR: "#E0A458",
        VERY_HIGH: "#8E2B2B", HIGH: "#C8553D", MEDIUM: "#E0A458", LOW: "#7FA7C9",
        OBSOLETE: "#7A7A7A", ARCHIVED: "#7A7A7A", CANCELLED: "#7A7A7A", UNTESTABLE: "#7A6F8F",
        READY: "#A0A8B5", NOT_RUN: "#A0A8B5"
    };

    
    var TIMELINE_VISIBLE = 4;

    var MULTI_FILTERS = ["projects", "authors", "reqStatus", "criticality", "category", "tcStatus", "importance",
        "nature", "type", "campaignStatus", "execStatus", "reportType"];

    var DEFAULT_STATE = {
        scope: "ALL", preset: "all", dateFrom: "", dateTo: "", dateField: "CREATED", groupBy: "AUTO",
        search: "", reqCoverage: "ALL", tcCoverage: "ALL", automatable: "ALL",
        projects: [], authors: [], reqStatus: [], criticality: [], category: [], tcStatus: [], importance: [],
        nature: [], type: [], campaignStatus: [], execStatus: [], reportType: []
    };

    /*
     * Description des filtres : groupe de portée, type de contrôle, clé de l'option serveur.
     * scope : null = toujours visible, sinon rubrique concernée.
     */
    var FILTER_DEFS = [
        { key: "reqStatus", scope: "REQUIREMENTS", kind: "multi", option: "requirementStatuses" },
        { key: "criticality", scope: "REQUIREMENTS", kind: "multi", option: "criticalities" },
        { key: "category", scope: "REQUIREMENTS", kind: "multi", option: "categories" },
        { key: "reqCoverage", scope: "REQUIREMENTS", kind: "select", values: ["ALL", "COVERED", "UNCOVERED"] },
        { key: "tcStatus", scope: "TEST_CASES", kind: "multi", option: "testCaseStatuses" },
        { key: "importance", scope: "TEST_CASES", kind: "multi", option: "importances" },
        { key: "nature", scope: "TEST_CASES", kind: "multi", option: "natures" },
        { key: "type", scope: "TEST_CASES", kind: "multi", option: "types" },
        { key: "tcCoverage", scope: "TEST_CASES", kind: "select", values: ["ALL", "COVERED", "UNCOVERED"] },
        { key: "automatable", scope: "TEST_CASES", kind: "select", values: ["ALL", "Y", "N", "M"] },
        { key: "campaignStatus", scope: "CAMPAIGNS", kind: "multi", option: "campaignStatuses" },
        { key: "execStatus", scope: "CAMPAIGNS", kind: "multi", option: "executionStatuses" },
        { key: "reportType", scope: "REPORTING", kind: "multi", option: "reportTypes" }
    ];

    /*
     * ------------------------------------------------------------------
     * Outils
     * ------------------------------------------------------------------
     */

    function esc(value) {
        return String(value === null || value === undefined ? "" : value)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    function clone(obj) {
        return JSON.parse(JSON.stringify(obj));
    }

    function pad(n) {
        return n < 10 ? "0" + n : String(n);
    }

    function isoDay(date) {
        return date.getFullYear() + "-" + pad(date.getMonth() + 1) + "-" + pad(date.getDate());
    }

    function Dashboard(root) {
        this.root = root;
        this.endpoint = root.getAttribute("data-endpoint");
        this.i18n = {};
        this.state = clone(DEFAULT_STATE);
        this.options = {};
        this.projects = [];
        this.requestId = 0;
        this.controller = null;
        this.timer = null;
        this.firstLoad = true;
        this.sort = {};
        this.lastData = null;

        try {
            this.i18n = JSON.parse(root.getAttribute("data-i18n") || "{}");
        } catch (e) {
            this.i18n = {};
        }

        this.lang = document.documentElement.lang || navigator.language || "fr";
    }

    Dashboard.prototype.t = function(key, fallback) {
        if (Object.prototype.hasOwnProperty.call(this.i18n, key)) {
            return this.i18n[key];
        }
        return fallback !== undefined ? fallback : this.humanize(key.split(".").pop());
    };

    Dashboard.prototype.humanize = function(code) {
        var text = String(code).replace(/^(CAT|NAT|TYP)_/, "").replace(/_/g, " ").toLowerCase();
        return text.charAt(0).toUpperCase() + text.slice(1);
    };

    Dashboard.prototype.valueLabel = function(code) {
        return this.t("value." + code, this.humanize(code));
    };

    Dashboard.prototype.color = function(code, index) {
        return VALUE_COLORS[code] || PALETTE[index % PALETTE.length];
    };

    Dashboard.prototype.number = function(n) {
        try {
            return new Intl.NumberFormat(this.lang).format(n);
        } catch (e) {
            return String(n);
        }
    };

    Dashboard.prototype.date = function(ms) {
        if (ms === null || ms === undefined) {
            return "";
        }
        try {
            return new Intl.DateTimeFormat(this.lang, { year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(ms));
        } catch (e) {
            return isoDay(new Date(ms));
        }
    };

    Dashboard.prototype.dateTime = function(ms) {
        try {
            return new Intl.DateTimeFormat(this.lang, { dateStyle: "short", timeStyle: "short" }).format(new Date(ms));
        } catch (e) {
            return new Date(ms).toLocaleString();
        }
    };

    Dashboard.prototype.periodLabel = function(period, group) {
        var parts = period.split("-");
        try {
            if (group === "MONTH") {
                return new Intl.DateTimeFormat(this.lang, { month: "short", year: "2-digit" })
                    .format(new Date(+parts[0], +parts[1] - 1, 1));
            }
            return new Intl.DateTimeFormat(this.lang, { day: "2-digit", month: "short" })
                .format(new Date(+parts[0], +parts[1] - 1, +parts[2]));
        } catch (e) {
            return period;
        }
    };

    Dashboard.prototype.scopeVisible = function(scope) {
        return this.state.scope === "ALL" || this.state.scope === scope;
    };

    /*
     * ------------------------------------------------------------------
     * Persistance des filtres
     * ------------------------------------------------------------------
     */

    Dashboard.prototype.restore = function() {
        try {
            var saved = JSON.parse(window.sessionStorage.getItem(STORAGE_KEY) || "null");
            if (saved && typeof saved === "object") {
                for (var key in DEFAULT_STATE) {
                    if (Object.prototype.hasOwnProperty.call(saved, key) && typeof saved[key] === typeof DEFAULT_STATE[key]) {
                        this.state[key] = saved[key];
                    }
                }
            }
        } catch (e) {
            // stockage indisponible : filtres par défaut
        }
    };

    Dashboard.prototype.persist = function() {
        try {
            window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(this.state));
        } catch (e) {
            // ignoré
        }
    };

    Dashboard.prototype.build = function() {
        this.syncControls();
    };

    Dashboard.prototype.segmented = function(key, values, labeler) {
        var html = '<div class="sqd-segmented" role="group" data-segment="' + key + '">';
        for (var i = 0;i < values.length;i++) {
            html += '<button type="button" data-value="' + esc(values[i]) + '" aria-pressed="false">' + esc(labeler(values[i])) + '</button>';
        }
        return html + '</div>';
    };

    Dashboard.prototype.select = function(key, values, labeler) {
        var html = '<select id="' + (key === "dateField" ? "sqdDateField" : key === "groupBy" ? "sqdGroupBy" : "sqd_" + key) + '" class="form-control" data-field="' + key + '">';
        for (var i = 0;i < values.length;i++) {
            html += '<option value="' + esc(values[i]) + '">' + esc(labeler(values[i])) + '</option>';
        }
        return html + '</select>';
    };

    Dashboard.prototype.multi = function(key) {
        return '<details class="sqd-multi" data-multi="' + key + '"><summary><span data-role="summary"></span></summary>' +
            '<div class="sqd-multi-panel" data-role="panel"></div></details>';
    };

    /*
     * Reporte l'état courant dans les contrôles du formulaire.
     */
    Dashboard.prototype.syncControls = function() {
        var s = this.state;
        var root = this.root;
        var i;

        var segments = root.querySelectorAll("[data-segment]");
        for (i = 0;i < segments.length;i++) {
            var key = segments[i].getAttribute("data-segment");
            var buttons = segments[i].querySelectorAll("button");
            for (var j = 0;j < buttons.length;j++) {
                buttons[j].setAttribute("aria-pressed", buttons[j].getAttribute("data-value") === String(s[key]) ? "true" : "false");
            }
        }

        var scopeInputs = root.querySelectorAll("input[name='scope']");

        for (i = 0;i < scopeInputs.length;i++) {
            scopeInputs[i].checked = scopeInputs[i].value === String(s.scope);
        }

        var periodInputs = root.querySelectorAll("input[name='preset']");

        for (i = 0;i < periodInputs.length;i++) {
            periodInputs[i].checked = periodInputs[i].value === String(s.preset);
        }

        var fields = root.querySelectorAll("[data-field]");
        for (i = 0;i < fields.length;i++) {
            var name = fields[i].getAttribute("data-field");
            if (typeof s[name] === "string" && fields[i].value !== s[name]) {
                fields[i].value = s[name];
            }
        }

        var groups = root.querySelectorAll("[data-scope-group]");
        for (i = 0;i < groups.length;i++) {
            groups[i].style.display = this.scopeVisible(groups[i].getAttribute("data-scope-group")) ? "" : "none";
        }

        for (i = 0;i < MULTI_FILTERS.length;i++) {
            this.renderMulti(MULTI_FILTERS[i]);
        }

        this.renderChips();
    };

    /*
     * ------------------------------------------------------------------
     * Listes à choix multiples
     * ------------------------------------------------------------------
     */

    Dashboard.prototype.optionValues = function(key) {
        var i;
        var result = [];

        if (key === "projects") {
            for (i = 0;i < this.projects.length;i++) {
                result.push({ value: String(this.projects[i].id), label: this.projects[i].name });
            }
            return result;
        }

        var optionKey = key === "authors" ? "authors" : null;
        for (i = 0;i < FILTER_DEFS.length;i++) {
            if (FILTER_DEFS[i].key === key) {
                optionKey = FILTER_DEFS[i].option;
            }
        }

        var raw = (optionKey && this.options[optionKey]) || [];
        for (i = 0;i < raw.length;i++) {
            result.push({ value: raw[i], label: key === "authors" ? raw[i] : this.valueLabel(raw[i]) });
        }

        var selected = this.state[key] || [];
        for (i = 0;i < selected.length;i++) {
            var found = false;
            for (var j = 0;j < result.length;j++) {
                if (result[j].value === selected[i]) {
                    found = true;
                }
            }
            if (!found) {
                result.push({ value: selected[i], label: key === "authors" ? selected[i] : this.valueLabel(selected[i]) });
            }
        }

        return result;
    };

    Dashboard.prototype.renderMulti = function(key) {
        var el = this.root.querySelector('[data-multi="' + key + '"]');
        if (!el) {
            return;
        }

        var values = this.optionValues(key);
        var selected = this.state[key] || [];
        var panel = el.querySelector('[data-role="panel"]');
        var summary = el.querySelector('[data-role="summary"]');
        var html = "";

        for (var i = 0;i < values.length;i++) {
            var checked = selected.indexOf(values[i].value) >= 0;
            html += '<label class="sqd-multi-option"><input type="checkbox" data-multi-key="' + key + '" value="' +
                esc(values[i].value) + '"' + (checked ? " checked" : "") + '> <span>' + esc(values[i].label) + '</span></label>';
        }

        if (!html) {
            html = '<div class="sqd-multi-empty">' + esc(this.t("filters.none-available")) + '</div>';
        }

        var signature = values.map(function(v) { return v.value; }).join("|") + "#" + selected.join("|");
        if (el.getAttribute("data-signature") !== signature) {
            panel.innerHTML = html;
            el.setAttribute("data-signature", signature);
        }

        var label = key === "projects" ? this.t("filter.projects.all") : this.t("filter." + key);
        var text = esc(label);
        if (selected.length > 0) {
            text = esc(key === "projects" ? this.t("filter.projects") : label) +
                ' <span class="sqd-multi-count">' + selected.length + '</span>';
        }
        summary.innerHTML = text;
    };

    /*
     * ------------------------------------------------------------------
     * Filtres actifs (pastilles)
     * ------------------------------------------------------------------
     */

    Dashboard.prototype.renderChips = function() {
        var chips = [];
        var s = this.state;
        var t = this.t.bind(this);
        var i;

        if (s.dateFrom || s.dateTo) {
            chips.push({
                label: t("filter.period") + " : " + (s.dateFrom ? this.date(Date.parse(s.dateFrom)) : "…") + " → " +
                    (s.dateTo ? this.date(Date.parse(s.dateTo)) : "…"), clear: ["dateFrom", "dateTo", "preset"]
            });
        }

        for (i = 0;i < MULTI_FILTERS.length;i++) {
            var key = MULTI_FILTERS[i];
            var list = s[key] || [];
            if (list.length === 0) {
                continue;
            }
            if (key !== "projects" && key !== "authors" && !this.filterInScope(key)) {
                continue;
            }
            var names = [];
            var options = this.optionValues(key);
            for (var j = 0;j < list.length;j++) {
                var name = list[j];
                for (var k = 0;k < options.length;k++) {
                    if (options[k].value === list[j]) {
                        name = options[k].label;
                    }
                }
                names.push(name);
            }
            chips.push({ label: t("filter." + key) + " : " + names.join(", "), clear: [key] });
        }

        if (s.search) {
            chips.push({ label: t("filter.search") + " : " + s.search, clear: ["search"] });
        }
        if (s.reqCoverage !== "ALL" && this.scopeVisible("REQUIREMENTS")) {
            chips.push({ label: t("reqCoverage." + s.reqCoverage), clear: ["reqCoverage"] });
        }
        if (s.tcCoverage !== "ALL" && this.scopeVisible("TEST_CASES")) {
            chips.push({ label: t("tcCoverage." + s.tcCoverage), clear: ["tcCoverage"] });
        }
        if (s.automatable !== "ALL" && this.scopeVisible("TEST_CASES")) {
            chips.push({ label: t("filter.automatable") + " : " + t("automatable." + s.automatable), clear: ["automatable"] });
        }

        var box = this.root.querySelector('[data-role="chips"]');
        var html = "";

        for (i = 0;i < chips.length;i++) {
            html += '<span class="sqd-chip">' + esc(chips[i].label) + '<button type="button" data-chip="' + i +
                '" aria-label="' + esc(t("filters.remove")) + '" title="' + esc(t("filters.remove")) + '">&times;</button></span>';
        }
        if (chips.length > 1) {
            html += '<button type="button" class="sqd-btn is-link" data-role="clear-all">' + esc(t("filters.clear-all")) + '</button>';
        }

        box.innerHTML = html;
        this.chips = chips;
    };

    Dashboard.prototype.filterInScope = function(key) {
        for (var i = 0;i < FILTER_DEFS.length;i++) {
            if (FILTER_DEFS[i].key === key) {
                return this.scopeVisible(FILTER_DEFS[i].scope);
            }
        }
        return true;
    };

    /*
     * ------------------------------------------------------------------
     * Évènements
     * ------------------------------------------------------------------
     */


    Dashboard.prototype.bind = function() {
        var self = this;
        var root = this.root;

        // ==========================================================
        // INTERRUPTEURS JALIOS
        // ==========================================================

        function readSwitch(name) {

            var $inputs = window.jQuery(root).find("input[name='" + name + "']");
            var $radios = $inputs.filter("[type='radio']");

            if ($radios.length) {
                return $radios.filter(":checked").val() === "true";
            }

            return $inputs.filter("[type='checkbox']").is(":checked");
        }

        // ==========================================================
        // AFFICHAGE DES FILTRES
        // ==========================================================

        // Switch principal : afficher ou masquer tous les filtres
        window.jQuery(root).on("change", ".sqd-filter-toggle", function() {

            var on = readSwitch("sqdToggleFilters");

            var filtersBody = root.querySelector("[data-role='filters-body']");

            if (filtersBody) {
                filtersBody.style.display = on ? "" : "none";
            }

        });

        // Switch secondaire : afficher ou masquer les filtres avancés
        window.jQuery(root).on("change", ".sqd-advanced-toggle", function() {

            var on = readSwitch("sqdToggleAdvancedFilters");

            var advancedBody = root.querySelector("[data-role='advanced-filters-body']");

            if (advancedBody) {
                advancedBody.style.display = on ? "" : "none";
            }

        });

        // ==========================================================
        // CLICS
        // ==========================================================

        root.addEventListener("click", function(event) {
            var target = event.target;
            var btn = target.closest ? target.closest("button") : null;

            if (!btn) {
                return;
            }

            var segment = btn.parentNode && btn.parentNode.getAttribute ?
                btn.parentNode.getAttribute("data-segment") : null;

            if (segment) {
                self.onSegment(segment, btn.getAttribute("data-value"));

            } else if (btn.getAttribute("data-role") === "refresh") {
                self.load(true);

            } else if (btn.getAttribute("data-role") === "reset") {
                self.reset();

            } else if (btn.getAttribute("data-role") === "clear-all") {
                self.reset();

            } else if (btn.hasAttribute("data-chip")) {
                var chip = self.chips[parseInt(btn.getAttribute("data-chip"), 10)];

                if (chip) {
                    for (var i = 0;i < chip.clear.length;i++) {
                        var key = chip.clear[i];

                        self.state[key] = key === "preset" ? "all" :
                            (Array.isArray(DEFAULT_STATE[key]) ? [] : DEFAULT_STATE[key]);
                    }

                    self.changed();
                }

            } else if (btn.hasAttribute("data-export")) {
                self.exportCsv(btn.getAttribute("data-export"));

            } else if (btn.hasAttribute("data-drill")) {
                self.drill(btn.getAttribute("data-drill"), btn.getAttribute("data-value"));

            } else if (btn.hasAttribute("data-sort")) {
                self.onSort(btn.getAttribute("data-table"), btn.getAttribute("data-sort"));
            }
        });

        root.addEventListener("change", function(event) {
            var target = event.target;

            if (target.name === "scope" && target.type === "radio") {

                if (target.checked) {
                    self.onSegment("scope", target.value);
                }

            } else if (target.name === "preset" && target.type === "radio") {

                if (target.checked) {
                    self.onSegment("preset", target.value);
                }

            } else if (target.hasAttribute("data-multi-key")) {
                var key = target.getAttribute("data-multi-key");
                var list = self.state[key].slice();
                var index = list.indexOf(target.value);

                if (target.checked && index < 0) {
                    list.push(target.value);

                } else if (!target.checked && index >= 0) {
                    list.splice(index, 1);
                }

                self.state[key] = list;
                self.changed(true);

            } else if (target.hasAttribute("data-field")) {
                var name = target.getAttribute("data-field");

                self.state[name] = target.value;

                if (name === "dateFrom" || name === "dateTo") {
                    self.state.preset = "custom";
                }

                self.changed();
            }
        });

        root.addEventListener("input", function(event) {
            var target = event.target;

            if (target.getAttribute("data-field") === "search") {

                self.state.search = target.value;

                window.clearTimeout(self.timer);

                self.timer = window.setTimeout(function() {
                    self.changed(true);
                }, 350);
            }
        });

        document.addEventListener("click", function(event) {
            var open = root.querySelectorAll("details.sqd-multi[open]");

            for (var i = 0;i < open.length;i++) {
                if (!open[i].contains(event.target)) {
                    open[i].removeAttribute("open");
                }
            }
        });

        root.addEventListener("keydown", function(event) {
            if (event.key === "Escape") {
                var open = root.querySelectorAll("details.sqd-multi[open]");

                for (var i = 0;i < open.length;i++) {
                    open[i].removeAttribute("open");
                }
            }
        });

        root.addEventListener("click", function(event) {
            var toggle = event.target.closest ? event.target.closest("[data-toggle-table]") : null;

            if (!toggle) {
                return;
            }

            var table = toggle.closest("table");
            var collapsed = table.classList.toggle("is-collapsed");

            toggle.setAttribute("aria-expanded", collapsed ? "false" : "true");
        });
    };


    Dashboard.prototype.onSegment = function(key, value) {
        this.state[key] = value;

        if (key === "preset") {
            this.applyPreset(value);
        }

        this.changed();
    };

    Dashboard.prototype.applyPreset = function(preset) {
        var today = new Date();

        if (preset === "all") {
            this.state.dateFrom = "";
            this.state.dateTo = "";
        } else if (preset === "year") {
            this.state.dateFrom = today.getFullYear() + "-01-01";
            this.state.dateTo = isoDay(today);
        } else {
            var from = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (parseInt(preset, 10) - 1));
            this.state.dateFrom = isoDay(from);
            this.state.dateTo = isoDay(today);
        }
    };

    Dashboard.prototype.reset = function() {
        this.state = clone(DEFAULT_STATE);
        this.changed();
    };

    /*
     * Filtrage par clic sur un graphique.
     */
    Dashboard.prototype.drill = function(key, value) {
        var list = this.state[key];

        if (!Array.isArray(list)) {
            return;
        }

        var index = list.indexOf(value);

        if (index >= 0) {
            list.splice(index, 1);
        } else {
            list.push(value);
        }

        this.changed();
    };

    Dashboard.prototype.changed = function(keepControls) {
        this.persist();

        if (keepControls) {
            // Évite de recréer les cases à cocher pendant la saisie.
            this.renderChips();
            this.updateMultiSummaries();
        } else {
            this.syncControls();
        }

        this.load(false);
    };

    Dashboard.prototype.updateMultiSummaries = function() {
        for (var i = 0;i < MULTI_FILTERS.length;i++) {
            var key = MULTI_FILTERS[i];
            var el = this.root.querySelector('[data-multi="' + key + '"] [data-role="summary"]');
            if (!el) {
                continue;
            }
            var count = (this.state[key] || []).length;
            var label = key === "projects" && count === 0 ? this.t("filter.projects.all") :
                (key === "projects" ? this.t("filter.projects") : this.t("filter." + key));
            el.innerHTML = esc(label) + (count > 0 ? ' <span class="sqd-multi-count">' + count + '</span>' : "");
        }
    };

    /*
     * ------------------------------------------------------------------
     * Requêtes
     * ------------------------------------------------------------------
     */

    Dashboard.prototype.query = function(extra) {
        var s = this.state;
        var params = [];

        function add(name, value) {
            if (value !== "" && value !== null && value !== undefined) {
                params.push(encodeURIComponent(name) + "=" + encodeURIComponent(value));
            }
        }

        add("scope", s.scope);
        add("dateField", s.dateField);
        add("groupBy", s.groupBy);
        add("dateFrom", s.dateFrom);
        add("dateTo", s.dateTo);
        add("search", s.search);
        add("reqCoverage", s.reqCoverage);
        add("tcCoverage", s.tcCoverage);
        add("automatable", s.automatable);

        for (var i = 0;i < MULTI_FILTERS.length;i++) {
            if (s[MULTI_FILTERS[i]].length > 0) {
                add(MULTI_FILTERS[i], s[MULTI_FILTERS[i]].join(","));
            }
        }

        for (var key in extra) {
            if (Object.prototype.hasOwnProperty.call(extra, key)) {
                add(key, extra[key]);
            }
        }

        return params.join("&");
    };

    Dashboard.prototype.fetchJson = function(url, signal) {
        var options = { credentials: "same-origin", headers: { "Accept": "application/json" } };

        if (signal) {
            options.signal = signal;
        }

        return fetch(url, options).then(function(response) {
            return response.text().then(function(text) {
                var json = null;

                try {
                    json = JSON.parse(text);
                } catch (e) {
                    json = null;
                }

                if (!response.ok || !json) {
                    throw new Error(json && json.error ? json.error : "HTTP " + response.status);
                }

                return json;
            });
        });
    };

    Dashboard.prototype.loadProjects = function() {
        var self = this;

        return this.fetchJson(this.endpoint + "?op=projects").then(function(json) {
            self.projects = json.projects || [];

            // Retire les projets sélectionnés qui ne sont plus accessibles.
            var known = {};
            for (var i = 0;i < self.projects.length;i++) {
                known[String(self.projects[i].id)] = true;
            }
            self.state.projects = self.state.projects.filter(function(id) {
                return known[id];
            });

            self.renderMulti("projects");
            self.renderChips();
        });
    };

    // Affiche l'animation Jalios "loading waves" dans la zone prévue de la JSPF.
    Dashboard.prototype.showWaves = function() {
        var box = this.root.querySelector('[data-role="waves"]');

        if (!box) {
            return;
        }

        box.hidden = false;

        if (window.jQuery && window.jQuery.fn.displayLoadingWaves) {
            window.jQuery(box).find(".ajax-loading-component-waves").remove();
            window.jQuery(box).displayLoadingWaves();
        }
    };

    Dashboard.prototype.hideWaves = function() {
        var box = this.root.querySelector('[data-role="waves"]');

        if (!box) {
            return;
        }

        if (window.jQuery) {
            window.jQuery(box).find(".ajax-loading-component-waves").remove();
        }

        box.hidden = true;
    };

    Dashboard.prototype.load = function(refresh) {
        var self = this;
        var id = ++this.requestId;

        if (this.controller && this.controller.abort) {
            this.controller.abort();
        }
        this.controller = window.AbortController ? new AbortController() : null;

        this.root.classList.add("is-loading");
        this.root.setAttribute("aria-busy", "true");

        if (this.firstLoad || refresh) {
            this.showWaves();
        }

        var url = this.endpoint + "?op=data&" + this.query(refresh ? { refresh: "true" } : {});

        return this.fetchJson(url, this.controller ? this.controller.signal : null).then(function(data) {
            if (id !== self.requestId) {
                return;
            }

            self.firstLoad = false;
            self.lastData = data;
            self.options = data.options || {};
            self.hideMessage();
            self.render(data);

            for (var i = 0;i < MULTI_FILTERS.length;i++) {
                if (MULTI_FILTERS[i] !== "projects") {
                    self.renderMulti(MULTI_FILTERS[i]);
                }
            }
            self.renderChips();
        }).catch(function(error) {
            if (id !== self.requestId || (error && error.name === "AbortError")) {
                return;
            }

            self.showError(error && error.message ? error.message : String(error));
        }).then(function() {
            if (id === self.requestId) {
                self.root.classList.remove("is-loading");
                self.root.removeAttribute("aria-busy");
                self.hideWaves();
            }
        });
    };

    Dashboard.prototype.exportCsv = function(dataset) {
        var details = this.root.querySelector('[data-role="export"]');
        if (details) {
            details.removeAttribute("open");
        }
        window.location.href = this.endpoint + "?op=export&dataset=" + encodeURIComponent(dataset) + "&" + this.query({});
    };

    /*
     * ------------------------------------------------------------------
     * Messages
     * ------------------------------------------------------------------
     */

    Dashboard.prototype.showMessage = function(text, level) {
        var box = this.root.querySelector('[data-role="message"]');
        box.className = "sqd-message is-visible" + (level ? " is-" + level : "");
        box.textContent = text;
    };

    Dashboard.prototype.showError = function(message) {
        var box = this.root.querySelector('[data-role="message"]');
        if (message === "NetworkError when attempting to fetch resource.") {
            message = "Erreur réseau lors de la tentative de récupération de la ressource.";
        }
        box.className = "sqd-message is-visible is-error";
        box.innerHTML = esc(this.t("state.error")) + " " + esc(message) +
            ' <button type="button" class="sqd-btn is-link" data-role="refresh">' + esc(this.t("state.retry")) + '</button>';
    };

    Dashboard.prototype.hideMessage = function() {
        var box = this.root.querySelector('[data-role="message"]');
        box.className = "sqd-message";
        box.textContent = "";
    };

    /*
     * ------------------------------------------------------------------
     * Rendu
     * ------------------------------------------------------------------
     */

    /*
     * Le squelette HTML (KPI, sections, panneaux, tableaux) est dans la JSPF
     * (doSquashTmViewsDashboard.jspf). Le JS ne fait que le remplir et
     * afficher / masquer les blocs selon les données reçues.
     */
    Dashboard.prototype.render = function(data) {
        var t = this.t.bind(this);
        var body = this.root.querySelector('[data-role="body"]');

        var updated = this.root.querySelector('[data-role="updated"]');
        updated.textContent = t("state.updated").replace("{0}", this.dateTime(data.dataTimestamp)) +
            (data.fromCache ? " (" + t("state.cache") + ")" : "");

        if (data.partial) {
            this.showMessage(t("state.partial"), "warning");
        }

        var loading = body.querySelector('[data-role="loading"]');
        if (loading) {
            loading.hidden = true;
        }

        this.fillKpis(body, data);
        this.fillSection(body, "requirements", data.requirements, this.fillRequirements);
        this.fillSection(body, "testCases", data.testCases, this.fillTestCases);
        this.fillSection(body, "campaigns", data.campaigns, this.fillCampaigns);
        this.fillSection(body, "reporting", data.reporting, this.fillReporting, data.squashUrl);
    };

    // Renseigne les cellules KPI de la JSPF (data-kpi = clé du JSON, data-format = number | percent).
    Dashboard.prototype.fillKpis = function(body, data) {
        var k = data.kpis || {};
        var box = body.querySelector('[data-role="kpis"]');
        var cells = box.querySelectorAll("[data-kpi]");

        for (var i = 0;i < cells.length;i++) {
            var cell = cells[i];
            var value = k[cell.getAttribute("data-kpi")];

            if (value === undefined || value === null) {
                cell.hidden = true;
                continue;
            }

            cell.hidden = false;
            cell.querySelector("strong").textContent = cell.getAttribute("data-format") === "percent" ?
                String(value).replace(".", this.decimalSeparator()) + " %" : this.number(value);
        }

        box.hidden = false;
    };

    Dashboard.prototype.decimalSeparator = function() {
        try {
            return new Intl.NumberFormat(this.lang).format(1.1).charAt(1);
        } catch (e) {
            return ".";
        }
    };

    // Affiche / masque une section de la JSPF et appelle la fonction qui la remplit.
    Dashboard.prototype.fillSection = function(body, key, data, filler, extra) {
        var section = body.querySelector('[data-section="' + key + '"]');

        if (!section) {
            return;
        }

        if (!data) {
            section.hidden = true;
            return;
        }

        var empty = data.total === 0;

        section.hidden = false;
        section.querySelector('[data-role="count"]').textContent = this.number(data.total);
        section.querySelector('[data-role="empty"]').hidden = !empty;
        section.querySelector('[data-role="content"]').hidden = empty;

        if (!empty) {
            filler.call(this, section, data, extra);
        }
    };

    // Place le graphique dans le panneau [data-panel] de la JSPF.
    Dashboard.prototype.fillPanel = function(section, name, html) {
        var box = section.querySelector('[data-panel="' + name + '"] [data-role="chart"]');

        if (box) {
            box.innerHTML = html;
        }
    };

    Dashboard.prototype.fillRequirements = function(section, r) {
        var t = this.t.bind(this);

        this.fillPanel(section, "byStatus", this.bars(r.byStatus, r.total, "reqStatus"));
        this.fillPanel(section, "byCriticality", this.bars(r.byCriticality, r.total, "criticality"));
        this.fillPanel(section, "byCategory", this.bars(r.byCategory, r.total, "category"));
        this.fillPanel(section, "coverage", this.donut([
            { key: "COVERED", label: t("chart.covered"), count: r.covered, color: "#3E8E5A" },
            { key: "UNCOVERED", label: t("chart.uncovered"), count: r.uncovered, color: "#C8553D" }
        ], r.coverageRate + " %", t("kpi.coverage")));
        this.fillPanel(section, "byProject", this.bars(r.byProject, r.total, null));
        this.fillPanel(section, "timeline", this.timeline(r.timeline));

        this.fillTable(section, "requirements", r.rows, r.truncated, r.total);
    };

    Dashboard.prototype.fillTestCases = function(section, c) {
        var t = this.t.bind(this);

        this.fillPanel(section, "byStatus", this.bars(c.byStatus, c.total, "tcStatus"));
        this.fillPanel(section, "byImportance", this.bars(c.byImportance, c.total, "importance"));
        this.fillPanel(section, "byNature", this.bars(c.byNature, c.total, "nature"));
        this.fillPanel(section, "byType", this.bars(c.byType, c.total, "type"));
        this.fillPanel(section, "linkage", this.donut([
            { key: "COVERED", label: t("chart.linked"), count: c.linked, color: "#3E8E5A" },
            { key: "UNCOVERED", label: t("chart.notLinked"), count: c.notLinked, color: "#C8553D" }
        ], c.linkedRate + " %", t("kpi.linked")));
        this.fillPanel(section, "byAutomation", this.bars(this.relabelAutomation(c.byAutomation), c.total, null));
        this.fillPanel(section, "byProject", this.bars(c.byProject, c.total, null));
        this.fillPanel(section, "timeline", this.timeline(c.timeline));

        this.fillTable(section, "testcases", c.rows, c.truncated, c.total);
    };

    Dashboard.prototype.relabelAutomation = function(items) {
        var self = this;
        return (items || []).map(function(item) {
            return { key: item.key, count: item.count, label: self.valueLabel(item.key) };
        });
    };

    Dashboard.prototype.fillCampaigns = function(section, c) {
        this.fillPanel(section, "campaignStatus", this.bars(c.byStatus, c.total, "campaignStatus"));
        this.fillPanel(section, "execStatus", this.donutFromDistribution(c.executionsByStatus, c.executionTotal, "execStatus"));
        this.fillPanel(section, "execByProject", this.bars(c.executionsByProject, c.executionTotal, null));
        this.fillPanel(section, "execTimeline", this.executionTimeline(c.executionTimeline));

        this.fillTable(section, "campaigns", c.rows, c.truncated, c.total);
    };

    Dashboard.prototype.fillReporting = function(section, r, squashUrl) {
        var link = section.querySelector('[data-role="squash-link"]');

        if (link) {
            if (squashUrl && /^https?:\/\//.test(squashUrl)) {
                link.href = squashUrl;
                link.hidden = false;
            } else {
                link.removeAttribute("href");
                link.hidden = true;
            }
        }

        this.fillPanel(section, "byReportType", this.bars(r.byType, r.total, "reportType"));
        this.fillPanel(section, "byProject", this.bars(r.byProject, r.total, null));

        this.fillTable(section, "reporting", r.rows, r.truncated, r.total);
    };

    /*
     * ------------------------------------------------------------------
     * Graphiques
     * ------------------------------------------------------------------
     */

    Dashboard.prototype.bars = function(items, total, filterKey) {
        if (!items || items.length === 0 || total === 0) {
            return '<p class="sqd-empty">' + esc(this.t("state.empty")) + '</p>';
        }

        var MAX = 8;
        var shown = items.slice(0, MAX);
        var others = 0;
        var i;

        for (i = MAX;i < items.length;i++) {
            others += items[i].count;
        }

        var max = items[0].count || 1;
        var html = '<ul class="sqd-bars">';

        for (i = 0;i < shown.length;i++) {
            var item = shown[i];
            var label = item.label || (filterKey === null && !/^[A-Z0-9_]+$/.test(item.key) ? item.key : this.valueLabel(item.key));
            var percent = Math.round(item.count * 1000 / total) / 10;
            var selected = filterKey && this.state[filterKey] && this.state[filterKey].indexOf(item.key) >= 0;
            var labelHtml = filterKey && item.key !== "UNDEFINED" ?
                '<button type="button" class="sqd-bar-label" data-drill="' + filterKey + '" data-value="' + esc(item.key) +
                '" title="' + esc(this.t("chart.filter-hint")) + '"' + (selected ? ' aria-pressed="true"' : "") + '>' + esc(label) + '</button>' :
                '<span class="sqd-bar-label" title="' + esc(label) + '">' + esc(label) + '</span>';

            html += '<li>' + labelHtml + '<span class="sqd-bar-track"><span class="sqd-bar-fill" style="width:' +
                Math.max(2, Math.round(item.count * 100 / max)) + '%;background:' + this.color(item.key, i) + '"></span></span>' +
                '<span class="sqd-bar-value">' + this.number(item.count) + '<small>' + percent + ' %</small></span></li>';
        }

        if (others > 0) {
            html += '<li><span class="sqd-bar-label">' + esc(this.t("chart.others")) + '</span><span class="sqd-bar-track">' +
                '<span class="sqd-bar-fill" style="width:' + Math.max(2, Math.round(others * 100 / max)) + '%;background:#A0A8B5"></span></span>' +
                '<span class="sqd-bar-value">' + this.number(others) + '<small>' + Math.round(others * 1000 / total) / 10 + ' %</small></span></li>';
        }

        return html + '</ul>';
    };

    Dashboard.prototype.donutFromDistribution = function(items, total, filterKey) {
        var segments = [];

        for (var i = 0;i < (items || []).length;i++) {
            segments.push({
                key: items[i].key,
                label: this.valueLabel(items[i].key),
                count: items[i].count,
                color: this.color(items[i].key, i),
                filterKey: filterKey
            });
        }

        return this.donut(segments, this.number(total), this.t("chart.total"));
    };

    Dashboard.prototype.donut = function(segments, centerValue, caption) {
        var total = 0;
        var i;

        for (i = 0;i < segments.length;i++) {
            total += segments[i].count;
        }

        if (total === 0) {
            return '<p class="sqd-empty">' + esc(this.t("state.empty")) + '</p>';
        }

        var radius = 44;
        var circumference = 2 * Math.PI * radius;
        var offset = 0;
        var svg = '<svg viewBox="0 0 120 120" role="img" aria-label="' + esc(caption) + " " + esc(centerValue) + '">' +
            '<circle cx="60" cy="60" r="' + radius + '" fill="none" stroke="#f0f2f6" stroke-width="16"></circle>';

        for (i = 0;i < segments.length;i++) {
            if (segments[i].count === 0) {
                continue;
            }
            var length = circumference * segments[i].count / total;
            svg += '<circle cx="60" cy="60" r="' + radius + '" fill="none" stroke="' + segments[i].color +
                '" stroke-width="16" stroke-dasharray="' + length.toFixed(2) + " " + (circumference - length).toFixed(2) +
                '" stroke-dashoffset="' + (-offset).toFixed(2) + '" transform="rotate(-90 60 60)"></circle>';
            offset += length;
        }

        svg += '<text x="60" y="61" text-anchor="middle" class="sqd-donut-value">' + esc(centerValue) + '</text>' +
            '<text x="60" y="76" text-anchor="middle" class="sqd-donut-caption">' + esc(caption) + '</text></svg>';

        var legend = '<ul class="sqd-legend">';

        for (i = 0;i < segments.length;i++) {
            var seg = segments[i];
            var label = '<span class="sqd-swatch" style="background:' + seg.color + '"></span>';

            if (seg.filterKey && seg.key !== "UNDEFINED") {
                label += '<button type="button" class="sqd-bar-label" data-drill="' + seg.filterKey + '" data-value="' + esc(seg.key) +
                    '" title="' + esc(this.t("chart.filter-hint")) + '">' + esc(seg.label) + '</button>';
            } else {
                label += '<span>' + esc(seg.label) + '</span>';
            }

            legend += '<li>' + label + '<span class="sqd-legend-value">' + this.number(seg.count) + '</span></li>';
        }

        return '<div class="sqd-donut">' + svg + legend + '</ul></div>';
    };

    Dashboard.prototype.timeline = function(timeline) {
        var points = (timeline && timeline.points) || [];

        if (points.length === 0) {
            return '<p class="sqd-empty">' + esc(this.t("state.empty")) + '</p>';
        }

        return this.windowedColumns(points, timeline.group, TIMELINE_VISIBLE);
    };

    /*
     * Histogramme « par période » : seules `visible` dates sont affichées à la fois,
     * les autres sont accessibles en faisant défiler le bloc horizontalement.
     * Construit en HTML/CSS (et non en SVG) : chaque colonne occupe 1/visible de la
     * largeur du bloc, quelle que soit cette largeur. L'axe vertical reste fixe.
     */
    Dashboard.prototype.windowedColumns = function(points, group, visible) {
        var max = 1;
        var i;
        var g;

        for (i = 0;i < points.length;i++) {
            max = Math.max(max, points[i].count || 0);
        }

        var step = Math.max(1, Math.ceil(max / 4));
        max = step * Math.ceil(max / step);

        var axis = "";
        var lines = "";

        for (g = 0;g <= max;g += step) {
            var pos = (100 * g / max).toFixed(2);
            axis += '<span style="bottom:' + pos + '%">' + g + '</span>';
            lines += '<i style="bottom:' + pos + '%"></i>';
        }

        var cols = "";

        for (i = 0;i < points.length;i++) {
            var label = this.periodLabel(points[i].period, group);
            var count = points[i].count || 0;
            var height = (100 * count / max).toFixed(2);

            cols += '<div class="sqd-tl-col" style="flex-basis:' + (100 / points.length).toFixed(4) + '%" title="' + esc(label + " : " + count) + '">' +
                '<div class="sqd-tl-plot"><div class="sqd-tl-bar" style="height:' + height + '%"></div></div>' +
                '<div class="sqd-tl-label">' + esc(label) + '</div></div>';
        }

        return '<div class="sqd-tl">' +
            '<div class="sqd-tl-axis" aria-hidden="true">' + axis + '</div>' +
            '<div class="sqd-tl-scroll" tabindex="0" role="region" aria-label="' + esc(this.t("chart.timeline")) + '">' +
            '<div class="sqd-tl-inner" style="width:' + (100 * points.length / visible).toFixed(4) + '%">' +
            '<div class="sqd-tl-lines" aria-hidden="true">' + lines + '</div>' + cols +
            '</div></div></div>';
    };

    Dashboard.prototype.executionTimeline = function(timeline) {
        var points = (timeline && timeline.points) || [];

        if (points.length === 0) {
            return '<p class="sqd-empty">' + esc(this.t("state.empty")) + '</p>';
        }

        return this.columns(points, timeline.group, [
            { field: "success", color: "#3E8E5A", label: this.t("chart.success") },
            { field: "failure", color: "#C0392B", label: this.t("chart.failure") },
            { field: "other", color: "#A0A8B5", label: this.t("chart.others") }
        ], true);
    };

    /*
     * Histogramme SVG. stacked : empile les séries (other = total - succès - échecs).
     */
    Dashboard.prototype.columns = function(points, group, series, stacked) {
        var height = 190;
        var top = 10;
        var bottom = 26;
        var left = 34;
        var slot = Math.max(22, Math.min(54, Math.floor(760 / points.length)));
        var width = left + slot * points.length + 8;
        var plotHeight = height - top - bottom;
        var max = 1;
        var i;
        var j;

        for (i = 0;i < points.length;i++) {
            points[i].other = Math.max(0, (points[i].count || 0) - (points[i].success || 0) - (points[i].failure || 0));
            max = Math.max(max, stacked ? points[i].count : points[i][series[0].field]);
        }

        var step = Math.max(1, Math.ceil(max / 4));
        max = step * Math.ceil(max / step);

        var svg = '<div class="sqd-timeline"><svg width="' + width + '" height="' + height + '" viewBox="0 0 ' + width + ' ' + height +
            '" role="img" aria-label="' + esc(this.t("chart.timeline")) + '">';

        for (var g = 0;g <= max;g += step) {
            var y = top + plotHeight - plotHeight * g / max;
            svg += '<line class="sqd-gridline" x1="' + left + '" x2="' + width + '" y1="' + y + '" y2="' + y + '"></line>' +
                '<text class="sqd-axis" x="' + (left - 6) + '" y="' + (y + 3) + '" text-anchor="end">' + g + '</text>';
        }

        var labelEvery = Math.ceil(points.length / Math.floor((width - left) / 62));

        for (i = 0;i < points.length;i++) {
            var p = points[i];
            var x = left + i * slot + slot * 0.18;
            var barWidth = slot * 0.64;
            var base = top + plotHeight;
            var tip = this.periodLabel(p.period, group) + " : " + p.count;

            svg += '<g><title>' + esc(tip) + '</title>';

            if (stacked) {
                for (j = 0;j < series.length;j++) {
                    var h = plotHeight * (p[series[j].field] || 0) / max;
                    if (h > 0) {
                        base -= h;
                        svg += '<rect x="' + x + '" y="' + base + '" width="' + barWidth + '" height="' + h + '" fill="' + series[j].color + '"></rect>';
                    }
                }
            } else {
                var hh = plotHeight * p[series[0].field] / max;
                svg += '<rect x="' + x + '" y="' + (base - hh) + '" width="' + barWidth + '" height="' + hh + '" fill="' + series[0].color + '"></rect>';
            }

            svg += '</g>';

            if (i % labelEvery === 0) {
                svg += '<text class="sqd-axis" x="' + (x + barWidth / 2) + '" y="' + (height - 8) + '" text-anchor="middle">' +
                    esc(this.periodLabel(p.period, group)) + '</text>';
            }
        }

        return svg + '</svg></div>';
    };

    /*
     * ------------------------------------------------------------------
     * Tableaux
     * ------------------------------------------------------------------
     */

    /*
     * Remplit le tableau de la JSPF : les colonnes viennent des <th data-col data-type data-cls>,
     * le JS ne génère que les lignes du <tbody> et l'état du tri (aria-sort).
     */
    Dashboard.prototype.fillTable = function(section, id, rows, truncated, total) {
        var self = this;
        var t = this.t.bind(this);
        var wrap = section.querySelector('[data-role="table-wrap"]');
        var empty = section.querySelector('[data-role="table-empty"]');
        var note = section.querySelector('[data-role="table-note"]');
        var heads = wrap.querySelectorAll("th[data-col]");
        var columns = [];
        var i;

        for (i = 0;i < heads.length;i++) {
            columns.push({
                key: heads[i].getAttribute("data-col"),
                type: heads[i].getAttribute("data-type") || "",
                cls: heads[i].getAttribute("data-cls") || ""
            });
        }

        if (!rows || rows.length === 0) {
            wrap.hidden = true;
            note.hidden = true;
            empty.hidden = false;
            return;
        }

        var sorted = rows.slice();
        var sort = this.sort[id];

        if (sort) {
            var column = null;
            for (var c = 0;c < columns.length;c++) {
                if (columns[c].key === sort.key) {
                    column = columns[c];
                }
            }

            sorted.sort(function(a, b) {
                var va = a[sort.key];
                var vb = b[sort.key];
                var numeric = column && (column.type === "number" || column.type === "date" || column.type === "progress");
                var result;

                if (va === undefined || va === null) {
                    return 1;
                }
                if (vb === undefined || vb === null) {
                    return -1;
                }

                result = numeric ? va - vb : String(va).localeCompare(String(vb), self.lang, { numeric: true, sensitivity: "base" });
                return sort.dir === "asc" ? result : -result;
            });
        }

        for (i = 0;i < heads.length;i++) {
            heads[i].setAttribute("aria-sort", sort && sort.key === columns[i].key ?
                (sort.dir === "asc" ? "ascending" : "descending") : "none");
        }

        var html = "";

        for (var r = 0;r < sorted.length;r++) {
            html += "<tr>";

            for (var k = 0;k < columns.length;k++) {
                html += this.cell(columns[k], sorted[r]);
            }

            html += "</tr>";
        }

        wrap.querySelector("tbody").innerHTML = html;
        wrap.hidden = false;
        empty.hidden = true;

        if (truncated) {
            note.textContent = t("table.truncated").replace("{0}", rows.length).replace("{1}", this.number(total));
            note.hidden = false;
        } else {
            note.hidden = true;
        }
    };

    Dashboard.prototype.cell = function(col, row) {
        var value = row[col.key];

        if (col.type === "value") {
            var color = this.color(value, 0);
            return '<td><span class="sqd-pill" style="background:' + color + '1f;border-color:' + color + '66">' +
                esc(this.valueLabel(value)) + '</span></td>';
        }

        if (col.type === "number") {
            return '<td class="is-number">' + this.number(value === undefined ? 0 : value) + '</td>';
        }

        if (col.type === "date") {
            return '<td>' + esc(this.date(value)) + '</td>';
        }

        if (col.type === "progress") {
            var percent = value === undefined ? 0 : value;
            return '<td class="is-number"><span class="sqd-progress"><span class="sqd-progress-track"><span class="sqd-progress-fill" style="width:' +
                percent + '%"></span></span>' + percent + ' %</span></td>';
        }

        return '<td' + (col.cls ? ' class="' + col.cls + '"' : "") + '>' + esc(value) + '</td>';
    };

    Dashboard.prototype.onSort = function(table, key) {
        var current = this.sort[table];

        if (current && current.key === key) {
            this.sort[table] = { key: key, dir: current.dir === "asc" ? "desc" : "asc" };
        } else {
            this.sort[table] = { key: key, dir: "asc" };
        }

        if (this.lastData) {
            this.render(this.lastData);
        }
    };

    /*
     * ------------------------------------------------------------------
     * Démarrage
     * ------------------------------------------------------------------
     */

    Dashboard.prototype.start = function() {
        var self = this;

        this.restore();
        this.build();
        this.bind();
        this.showWaves();

        this.loadProjects().catch(function() {
            // Les projets seront aussi déductibles des erreurs de chargement principales.
        }).then(function() {
            self.load(false);
        });
    };

    var MAIN_FILL_CLASS = "sqd-main-fill";

    // Colore tous les conteneurs parents du dashboard, jusqu'à la racine de l'application
    // (celle qui contient aussi la sidebar), pour que le fond couvre toute la zone principale.
    function markMainContainers(root) {
        var node = root.parentElement;

        while (node && node !== document.body && !node.querySelector(".app-sidebar")) {
            node.classList.add(MAIN_FILL_CLASS);
            node = node.parentElement;
        }
    }

    // Retire le fond quand une autre vue (import, administration) est affichée.
    function clearMainContainers() {
        var marked = document.querySelectorAll("." + MAIN_FILL_CLASS);

        for (var i = 0;i < marked.length;i++) {
            marked[i].classList.remove(MAIN_FILL_CLASS);
        }
    }

    function init() {
        var root = document.getElementById("squashTmDashboard");

        if (!root) {
            clearMainContainers();
            return;
        }

        markMainContainers(root);

        if (root.getAttribute("data-initialized") === "true") {
            return;
        }

        root.setAttribute("data-initialized", "true");
        new Dashboard(root).start();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }

    // La vue peut être injectée après coup par le rafraîchissement AJAX de JCMS.
    if (window.MutationObserver) {
        new MutationObserver(function() {
            init();
        }).observe(document.documentElement, { childList: true, subtree: true });
    }
})();