/* TVApp REMOTEEDIT-008: web panel logic. Vanilla JS + fetch; talks to the
 * same /api/v1 endpoints as the phone client. Token is kept in memory by
 * default; persisted in localStorage after pairing (single-user LAN panel).
 * Source management sprint: the Sources card is the primary workflow
 * (add/update/delete/refresh IPTV + XMLTV), plus the per-source channel
 * picker ("kanal seçme") that drives the selection endpoints. */
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
    dragIndex: null,
    sources: [],
    imports: [],
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
    state.rows.forEach(function (row, index) {
      var tr = document.createElement("tr");
      tr.dataset.key = row.sourceKey;
      tr.draggable = true;

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
      attachDragHandlers(tr, index);
      tbody.appendChild(tr);
    });
    updateSelectionInfo();
  }

  /* Drag-to-reorder: on drop the moved row's new position is converted to a
   * sortOrder patch (position between neighbors) and sent to the TV. */
  function attachDragHandlers(tr, index) {
    tr.addEventListener("dragstart", function (event) {
      state.dragIndex = index;
      tr.classList.add("dragging");
      if (event.dataTransfer) event.dataTransfer.effectAllowed = "move";
    });
    tr.addEventListener("dragend", function () {
      tr.classList.remove("dragging");
    });
    tr.addEventListener("dragover", function (event) {
      event.preventDefault();
      tr.classList.add("drag-over");
    });
    tr.addEventListener("dragleave", function () {
      tr.classList.remove("drag-over");
    });
    tr.addEventListener("drop", function (event) {
      event.preventDefault();
      tr.classList.remove("drag-over");
      var from = state.dragIndex;
      var to = index;
      if (from == null || from === to) return;
      reorder(from, to);
    });
  }

  function reorder(fromIndex, toIndex) {
    var moved = state.rows.splice(fromIndex, 1)[0];
    state.rows.splice(toIndex, 0, moved);
    renderRows();
    var before = state.rows[toIndex - 1];
    var after = state.rows[toIndex + 1];
    var newOrder;
    if (before && after) {
      newOrder = Math.floor((rowSortKey(before) + rowSortKey(after)) / 2);
    } else if (before) {
      newOrder = rowSortKey(before) + 1;
    } else if (after) {
      newOrder = rowSortKey(after) - 1;
    } else {
      newOrder = 0;
    }
    api("/api/v1/channels/" + encodeURIComponent(moved.sourceKey), {
      method: "PATCH",
      bodyJson: { revision: moved.revision, sortOrder: newOrder },
    }).then(refresh).catch(function (error) {
      alert("Sıralama kaydedilemedi: " + error.message);
      return refresh();
    });
  }

  function rowSortKey(row) {
    // The list endpoint orders rows as the TV shows them; derive an integer
    // position from the loaded page. Pages are contiguous, so the index works.
    return Math.max(0, state.rows.indexOf(row));
  }

  /* ---------- Source management (primary workflow) ---------- */

  function loadSources() {
    return api("/api/v1/sources").then(function (json) {
      state.sources = json.sources || [];
      renderSources();
    }).catch(function () { /* panel keeps working without sources */ });
  }

  function renderSources() {
    var tbody = $("sourceRows");
    tbody.innerHTML = "";
    if (!state.sources.length) {
      var empty = document.createElement("tr");
      var td = document.createElement("td");
      td.colSpan = 6;
      td.className = "muted";
      td.textContent = "Henüz kaynak yok. Aşağıdan IPTV listesi veya XMLTV EPG ekleyin.";
      empty.appendChild(td);
      tbody.appendChild(empty);
      return;
    }
    state.sources.forEach(function (source) {
      var tr = document.createElement("tr");

      var kind = document.createElement("td");
      kind.className = "col-src";
      var tag = document.createElement("span");
      tag.className = "src " + (source.kind === "xmltv" ? "src-epg" : "");
      tag.textContent = source.kind === "xmltv" ? "XMLTV" : "IPTV";
      kind.appendChild(tag);

      var name = document.createElement("td");
      name.className = "name";
      name.textContent = source.name;

      var counts = document.createElement("td");
      counts.className = "col-num";
      counts.textContent = source.kind === "xmltv"
        ? (source.channelCount + " kanal")
        : (source.channelCount + " / " + (source.selectedCount || 0) + " seçili");

      var state2 = document.createElement("td");
      state2.className = "col-flags";
      if (source.error) {
        var err = document.createElement("span");
        err.className = "hid";
        err.textContent = "hata";
        err.title = source.error;
        state2.appendChild(err);
      } else {
        state2.textContent = "—";
      }

      var actions = document.createElement("td");
      actions.className = "col-actions";

      if (source.kind === "iptv") {
        var pick = actionButton("Kanal seç", function () { openSelectionPicker(source); });
        actions.appendChild(pick);
      }
      if (source.urlKind) {
        actions.appendChild(actionButton("Yenile", function () { mutateSource("refresh", source); }));
        actions.appendChild(actionButton("Adres", function () { changeSourceUrl(source); }));
      }
      actions.appendChild(actionButton("Sil", function () { deleteSource(source); }));

      tr.appendChild(kind);
      tr.appendChild(name);
      tr.appendChild(counts);
      tr.appendChild(state2);
      tr.appendChild(actions);
      tbody.appendChild(tr);
    });
  }

  function actionButton(label, handler) {
    var button = document.createElement("button");
    button.className = "row-action";
    button.textContent = label;
    button.addEventListener("click", handler);
    return button;
  }

  function mutateSource(operation, source) {
    api("/api/v1/sources/" + operation, {
      method: "POST",
      bodyJson: { kind: source.kind, id: source.id },
    }).then(function () {
      pollImports();
      return loadSources();
    }).catch(function (error) { alert(error.message); });
  }

  function deleteSource(source) {
    var label = source.kind === "xmltv" ? "XMLTV kaynağı" : "IPTV listesi";
    if (!confirm('"' + source.name + '" ' + label + ' silinsin mi? Kanalları/programlarıyla birlikte silinir.')) return;
    api("/api/v1/sources/delete", {
      method: "POST",
      bodyJson: { kind: source.kind, id: source.id },
    }).then(function () {
      return loadSources();
    }).then(refresh).catch(function (error) { alert(error.message); });
  }

  function changeSourceUrl(source) {
    var urlInput = document.createElement("input");
    urlInput.placeholder = "http://… yeni adres";
    urlInput.style.width = "100%";
    var overlay = simpleDialog(
      source.kind === "xmltv" ? "XMLTV adresini güncelle" : "IPTV adresini güncelle",
      [["Adres", urlInput]],
      function () {
        var url = urlInput.value.trim();
        if (!/^https?:\/\//.test(url)) { alert("http(s) adresi gerekli."); return false; }
        api("/api/v1/sources/refresh", {
          method: "POST",
          bodyJson: { kind: source.kind, id: source.id, url: url },
        }).then(function () {
          pollImports();
          return loadSources();
        }).catch(function (error) { alert(error.message); });
        return true;
      }
    );
    $("app").appendChild(overlay);
  }

  function addSource(kind) {
    var urlInput = document.createElement("input");
    urlInput.placeholder = kind === "xmltv" ? "http://… XMLTV adresi" : "http://… M3U adresi";
    urlInput.style.width = "100%";
    var nameInput = document.createElement("input");
    nameInput.placeholder = "Ad (isteğe bağlı)";
    nameInput.style.width = "100%";
    var overlay = simpleDialog(
      kind === "xmltv" ? "XMLTV EPG kaynağı ekle" : "IPTV listesi ekle",
      [["Adres", urlInput], ["Ad", nameInput]],
      function () {
        var url = urlInput.value.trim();
        if (!/^https?:\/\//.test(url)) { alert("http(s) adresi gerekli."); return false; }
        api("/api/v1/imports", {
          method: "POST",
          bodyJson: { url: url, name: nameInput.value.trim(), kind: kind === "xmltv" ? "xmltv" : "iptv" },
        }).then(function () {
          $("importsCard").classList.remove("hidden");
          pollImports();
        }).catch(function (error) { alert(error.message); });
        return true;
      }
    );
    $("app").appendChild(overlay);
  }

  function simpleDialog(title, fields, onSave) {
    var overlay = document.createElement("div");
    overlay.className = "card";
    overlay.innerHTML = "<h2>" + escapeHtml(title) + "</h2>";
    var wrap = document.createElement("div");
    wrap.className = "edit-grid";
    fields.forEach(function (field) {
      var label = document.createElement("label");
      label.textContent = field[0];
      wrap.appendChild(label);
      wrap.appendChild(field[1]);
    });
    overlay.appendChild(wrap);
    var actions = document.createElement("div");
    actions.className = "edit-actions";
    var save = document.createElement("button");
    save.textContent = "Kaydet";
    var cancel = document.createElement("button");
    cancel.textContent = "Vazgeç";
    actions.appendChild(save); actions.appendChild(cancel);
    overlay.appendChild(actions);
    cancel.addEventListener("click", function () { overlay.remove(); });
    save.addEventListener("click", function () {
      if (onSave() !== false) overlay.remove();
    });
    return overlay;
  }

  /* Channel picker ("kanal seçme"): paged catalog for one IPTV source with
   * checkboxes; every change is sent as a small delta to the selection API so
   * huge catalogs never travel as one payload. */
  function openSelectionPicker(source) {
    var overlay = document.createElement("div");
    overlay.className = "card picker-card";
    overlay.innerHTML =
      "<h2>Kanal seç: " + escapeHtml(source.name) + "</h2>" +
      '<div class="picker-toolbar">' +
      '<input id="pickSearch" placeholder="Kanal ara…" autocomplete="off">' +
      '<button id="pickDone">Bitti</button>' +
      "</div>" +
      '<div class="table-wrap"><table class="picker-table"><tbody id="pickRows"></tbody></table></div>' +
      '<p class="hint" id="pickInfo">Yükleniyor…</p>';
    $("app").appendChild(overlay);

    var anchor = null;
    var currentQuery = "";
    var loadedCount = 0;
    var selectedTotal = source.selectedCount || 0;

    function pickerApi(params) {
      var query = "/api/v1/channels?limit=200&sourceId=" + encodeURIComponent(source.id) + params;
      return api(query);
    }

    function loadPickerPage(reset) {
      if (reset) { anchor = null; loadedCount = 0; }
      var suffix = "";
      if (!reset && anchor) suffix = "&after=" + encodeURIComponent(anchor);
      if (currentQuery) suffix += "&q=" + encodeURIComponent(currentQuery);
      pickerApi(suffix).then(function (json) {
        var page = json.channels || [];
        if (reset) $("pickRows").innerHTML = "";
        if (page.length) anchor = page[page.length - 1].sourceKey;
        loadedCount += page.length;
        page.forEach(function (row) { appendPickerRow(row); });
        $("pickInfo").textContent = loadedCount + " kanal gösteriliyor · " +
          selectedTotal + " seçili · aşağı kaydırınca devam yüklenir";
        if (!page.length && !currentQuery) $("pickInfo").textContent = "Bu kaynakta kanal yok.";
      }).catch(function (error) {
        $("pickInfo").textContent = "Hata: " + error.message;
      });
    }

    function appendPickerRow(row) {
      var tr = document.createElement("tr");
      var td = document.createElement("td");
      var label = document.createElement("label");
      label.className = "pick-row";
      var box = document.createElement("input");
      box.type = "checkbox";
      box.checked = row.inMainList === true;
      box.addEventListener("change", function () {
        var body = {};
        if (box.checked) { body.add = [row.sourceKey]; selectedTotal++; }
        else { body.remove = [row.sourceKey]; selectedTotal--; }
        api("/api/v1/sources/selection/" + source.id, { method: "POST", bodyJson: body })
          .then(function () {
            $("pickInfo").textContent = loadedCount + " kanal gösteriliyor · " +
              selectedTotal + " seçili · aşağı kaydırınca devam yüklenir";
          })
          .catch(function (error) {
            box.checked = !box.checked;
            alert(error.message);
          });
      });
      var text = document.createElement("span");
      text.textContent = (row.displayNumber ? row.displayNumber + "  " : "") + row.displayName;
      label.appendChild(box);
      label.appendChild(text);
      td.appendChild(label);
      tr.appendChild(td);
      $("pickRows").appendChild(tr);
    }

    overlay.querySelector("#pickSearch").addEventListener("input", function (event) {
      clearTimeout(openSelectionPicker.timer);
      openSelectionPicker.timer = setTimeout(function () {
        currentQuery = event.target.value.trim();
        loadPickerPage(true);
      }, 300);
    });
    overlay.querySelector("#pickDone").addEventListener("click", function () {
      overlay.remove();
      loadSources();
      refresh();
    });

    loadPickerPage(true);
  }

  function pollImports() {
    api("/api/v1/imports").then(function (json) {
      var items = json.imports || [];
      state.imports = items;
      if (!items.length) return;
      $("importsCard").classList.remove("hidden");
      var tbody = $("importRows");
      tbody.innerHTML = "";
      items.forEach(function (item) {
        var tr = document.createElement("tr");
        [item.id, item.name, item.kind === "xmltv" ? "XMLTV" : "IPTV", item.status, item.importedChannels]
          .forEach(function (value) {
            var td = document.createElement("td");
            td.textContent = value;
            tr.appendChild(td);
          });
        if (item.error) {
          var td = document.createElement("td");
          td.textContent = item.error;
          td.className = "error";
          tr.appendChild(td);
        }
        tbody.appendChild(tr);
      });
      loadSources();
    }).catch(function () { /* ignore */ });
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
    loadSources();
    setInterval(pollEvents, 1500);
  }

  function boot() {
    loadToken();
    $("pairBtn").addEventListener("click", pair);
    $("pairCode").addEventListener("keydown", function (event) {
      if (event.key === "Enter") pair();
    });
    $("refreshBtn").addEventListener("click", function () { refresh(); loadSources(); });
    $("moreBtn").addEventListener("click", function () { loadPage(false); });
    $("addSourceBtn").addEventListener("click", function () { addSource("iptv"); });
    $("addXmltvBtn").addEventListener("click", function () { addSource("xmltv"); });
    setInterval(pollImports, 3000);
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
