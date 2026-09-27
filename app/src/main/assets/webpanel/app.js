/* TVApp REMOTEEDIT-008: web panel logic. Vanilla JS + fetch; talks to the
 * same /api/v1 endpoints as the phone client. Token is kept in memory by
 * default; persisted in localStorage after pairing (single-user LAN panel). */
(function () {
  "use strict";

  var state = {
    token: null,
    rows: [],
    cursor: null,
    query: "",
    selected: {},
    version: 1,
    polling: false,
  };

  var $ = function (id) { return document.getElementById(id); };

  function api(path, options) {
    options = options || {};
    options.headers = options.headers || {};
    if (state.token) options.headers["Authorization"] = "Bearer " + state.token;
    if (options.bodyJson) {
      options.headers["Content-Type"] = "application/json";
      options.body = JSON.stringify(options.bodyJson);
      delete options.bodyJson;
    }
    return fetch(path, options).then(function (response) {
      return response.text().then(function (text) {
        var json = {};
        try { json = text ? JSON.parse(text) : {}; } catch (e) { /* ignore */ }
        if (!response.ok) {
          var error = new Error(json.message || ("HTTP " + response.status));
          error.status = response.status;
          error.json = json;
          throw error;
        }
        return json;
      });
    });
  }

  function setBadge(text, kind) {
    var badge = $("connState");
    badge.textContent = text;
    badge.className = "badge" + (kind ? " " + kind : "");
  }

  function loadToken() {
    try { state.token = localStorage.getItem("tvapp_remote_token"); } catch (e) { /* ignore */ }
  }

  function saveToken(token) {
    state.token = token;
    try { localStorage.setItem("tvapp_remote_token", token); } catch (e) { /* ignore */ }
  }

  function pair() {
    var code = ($("pairCode").value || "").trim();
    $("pairError").textContent = "";
    if (!/^\d{6}$/.test(code)) {
      $("pairError").textContent = "6 haneli kodu girin.";
      return;
    }
    api("/api/v1/pair", {
      method: "POST",
      bodyJson: { code: code, deviceName: "Web panosu" },
    }).then(function (json) {
      saveToken(json.token);
      enterMain();
    }).catch(function (error) {
      $("pairError").textContent = error.message || "Eşleştirme başarısız.";
    });
  }

  function loadPage(reset) {
    var query = "/api/v1/channels?limit=200";
    if (!reset && state.cursor) query += "&after=" + encodeURIComponent(state.cursor);
    if (state.query) query += "&q=" + encodeURIComponent(state.query);
    return api(query).then(function (json) {
      var page = json.channels || [];
      state.rows = reset ? page : state.rows.concat(page);
      if (page.length) state.cursor = page[page.length - 1].sourceKey;
      renderRows();
      $("pageInfo").textContent = state.rows.length + " kanal";
      $("moreBtn").classList.toggle("hidden", page.length === 0);
    });
  }

  function renderRows() {
    var tbody = $("rows");
    tbody.innerHTML = "";
    state.rows.forEach(function (row) {
      var tr = document.createElement("tr");
      tr.dataset.key = row.sourceKey;

      var check = document.createElement("td");
      check.className = "col-check";
      var box = document.createElement("input");
      box.type = "checkbox";
      box.checked = !!state.selected[row.sourceKey];
      box.addEventListener("change", function () {
        if (box.checked) state.selected[row.sourceKey] = true;
        else delete state.selected[row.sourceKey];
        updateSelectionInfo();
      });
      check.appendChild(box);

      var num = document.createElement("td");
      num.className = "col-num";
      num.textContent = row.displayNumber;

      var name = document.createElement("td");
      name.className = "name";
      name.textContent = row.displayName;

      var src = document.createElement("td");
      src.className = "col-src";
      var tag = document.createElement("span");
      tag.className = "src";
      tag.textContent = row.source;
      src.appendChild(tag);

      var flags = document.createElement("td");
      flags.className = "col-flags flags";
      if (row.favorite) flags.innerHTML += '<span class="fav">★</span> ';
      if (row.hidden) flags.innerHTML += '<span class="hid">gizli</span>';

      tr.appendChild(check);
      tr.appendChild(num);
      tr.appendChild(name);
      tr.appendChild(src);
      tr.appendChild(flags);
      tr.addEventListener("dblclick", function () { editRow(row); });
      tbody.appendChild(tr);
    });
    updateSelectionInfo();
  }

  function updateSelectionInfo() {
    var count = Object.keys(state.selected).length;
    $("selectionInfo").textContent = count ? count + " seçili" : "";
    $("batchFav").classList.toggle("hidden", count === 0);
    $("batchHide").classList.toggle("hidden", count === 0);
  }

  function refresh() {
    state.cursor = null;
    return loadPage(true);
  }

  function editRow(row) {
    api("/api/v1/channels/" + encodeURIComponent(row.sourceKey)).then(function (full) {
      openEditor(full);
    }).catch(function (error) { alert(error.message); });
  }

  function openEditor(full) {
    var overlay = document.createElement("div");
    overlay.className = "card";
    overlay.innerHTML =
      "<h2>Kanalı düzenle</h2>" +
      '<div class="edit-grid">' +
      '<label for="eName">Özel ad</label><input id="eName" value="' +
      escapeHtml(full.customName || "") + '" placeholder="' + escapeHtml(full.displayName) + '">' +
      '<label for="eNumber">Özel numara</label><input id="eNumber" inputmode="numeric" value="' +
      (full.customNumber == null ? "" : full.customNumber) + '">' +
      '<label for="eGroup">Grup</label><select id="eGroup"></select>' +
      '<label>Favori</label><input type="checkbox" id="eFav"' + (full.favorite ? " checked" : "") + ">" +
      '<label>Gizli</label><input type="checkbox" id="eHid"' + (full.hidden ? " checked" : "") + ">" +
      "</div>" +
      '<div class="edit-actions">' +
      '<button id="eSave">Kaydet</button>' +
      '<button id="eClearName">Adı sıfırla</button>' +
      '<button id="eCancel">Vazgeç</button>' +
      "</div>";
    $("app").appendChild(overlay);

    api("/api/v1/groups").then(function (json) {
      var select = overlay.querySelector("#eGroup");
      var none = document.createElement("option");
      none.value = "";
      none.textContent = "(grup yok)";
      select.appendChild(none);
      (json.groups || []).forEach(function (group) {
        var item = document.createElement("option");
        item.value = group.id;
        item.textContent = group.name;
        if (full.groupId === group.id) item.selected = true;
        select.appendChild(item);
      });
    }).catch(function () { /* groups optional */ });

    overlay.querySelector("#eCancel").addEventListener("click", function () { overlay.remove(); });
    overlay.querySelector("#eSave").addEventListener("click", function () {
      var patch = { revision: full.revision };
      var name = overlay.querySelector("#eName").value.trim();
      var number = overlay.querySelector("#eNumber").value.trim();
      var groupId = overlay.querySelector("#eGroup").value;
      patch.favorite = overlay.querySelector("#eFav").checked;
      patch.hidden = overlay.querySelector("#eHid").checked;
      if (name) patch.customName = name;
      if (number) patch.customNumber = parseInt(number, 10);
      patch.groupId = groupId ? parseInt(groupId, 10) : null;
      api("/api/v1/channels/" + encodeURIComponent(full.sourceKey), {
        method: "PATCH",
        bodyJson: patch,
      }).then(function () { overlay.remove(); return refresh(); })
        .catch(function (error) { alert(error.message); });
    });
    overlay.querySelector("#eClearName").addEventListener("click", function () {
      api("/api/v1/channels/" + encodeURIComponent(full.sourceKey), {
        method: "PATCH",
        bodyJson: { revision: full.revision, clearCustomName: true },
      }).then(function () { overlay.remove(); return refresh(); })
        .catch(function (error) { alert(error.message); });
    });
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  function batch(patch) {
    var keys = Object.keys(state.selected);
    if (!keys.length) return;
    var ops = keys.map(function (key) {
      var row = state.rows.find(function (item) { return item.sourceKey === key; });
      var op = { sourceKey: key, revision: row ? row.revision : 0 };
      if ("favorite" in patch) op.favorite = patch.favorite;
      if ("hidden" in patch) op.hidden = patch.hidden;
      return op;
    });
    api("/api/v1/channels/batch", { method: "POST", bodyJson: { ops: ops } })
      .then(function (json) {
        var conflicts = (json.results || []).filter(function (item) { return item.status === "conflict"; });
        if (conflicts.length) {
          alert(conflicts.length + " kanal TV tarafında değişmiş; liste yenileniyor.");
        }
        state.selected = {};
        return refresh();
      })
      .catch(function (error) { alert(error.message); });
  }

  /* REMOTEEDIT-005: long-poll; server answers as soon as the version moves. */
  function pollEvents() {
    if (state.polling || !state.token) return;
    state.polling = true;
    api("/api/v1/events?since=" + state.version)
      .then(function (json) {
        state.version = json.version || state.version;
        if (json.changed) return refresh();
      })
      .catch(function () { /* transient; next tick retries */ })
      .then(function () { state.polling = false; });
  }

  function enterMain() {
    $("pairView").classList.add("hidden");
    $("mainView").classList.remove("hidden");
    setBadge("Bağlı", "ok");
    refresh();
    setInterval(pollEvents, 1500);
  }

  function boot() {
    loadToken();
    $("pairBtn").addEventListener("click", pair);
    $("pairCode").addEventListener("keydown", function (event) {
      if (event.key === "Enter") pair();
    });
    $("refreshBtn").addEventListener("click", refresh);
    $("moreBtn").addEventListener("click", function () { loadPage(false); });
    $("batchFav").addEventListener("click", function () { batch({ favorite: true }); });
    $("batchHide").addEventListener("click", function () { batch({ hidden: true }); });
    var searchTimer = null;
    $("search").addEventListener("input", function (event) {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(function () {
        state.query = event.target.value.trim();
        refresh();
      }, 300);
    });
    $("checkAll").addEventListener("change", function (event) {
      state.selected = {};
      if (event.target.checked) {
        state.rows.forEach(function (row) { state.selected[row.sourceKey] = true; });
      }
      renderRows();
    });

    api("/api/v1/ping").then(function () {
      if (state.token) {
        enterMain();
      } else {
        setBadge("Eşleştirme gerekli");
        $("pairView").classList.remove("hidden");
      }
    }).catch(function () {
      setBadge("TV'ye ulaşılamıyor", "err");
      $("pairView").classList.remove("hidden");
    });
  }

  document.addEventListener("DOMContentLoaded", boot);
})();
