param([string]$Serial = "")

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

function Find-Adb {
    $candidates = @(
        $(if ($env:ANDROID_SDK_ROOT) { Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe" }),
        $(if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" }),
        "D:\Android\Sdk\platform-tools\adb.exe"
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }
    if (-not $candidates) { throw "adb.exe bulunamadi." }
    return $candidates[0]
}

$script:adb = Find-Adb
$script:deviceSerial = $Serial
$script:statusLabel = $null
$script:longPress = $null

function Refresh-Device {
    if (-not $script:deviceSerial) {
        $line = & $script:adb devices |
            Where-Object { $_ -match '^emulator-\d+\s+device' } |
            Select-Object -First 1
        if ($line) {
            $script:deviceSerial = ([regex]::Match($line, '^(emulator-\d+)')).Groups[1].Value
        }
    }
    if ($script:statusLabel) {
        $connected = [bool]$script:deviceSerial
        $script:statusLabel.Text = if ($connected) {
            "Bagli: $script:deviceSerial"
        } else {
            "Emulator bulunamadi"
        }
        $script:statusLabel.ForeColor = if ($connected) {
            [System.Drawing.Color]::FromArgb(38, 217, 127)
        } else {
            [System.Drawing.Color]::FromArgb(255, 59, 78)
        }
    }
}

function Send-TvKey([int]$KeyCode) {
    Refresh-Device
    if (-not $script:deviceSerial) { return }
    $arguments = @('-s', $script:deviceSerial, 'shell', 'input', 'keyevent')
    if ($script:longPress.Checked) { $arguments += '--longpress' }
    $arguments += $KeyCode
    & $script:adb @arguments | Out-Null
    $script:statusLabel.Text = if ($LASTEXITCODE -eq 0) {
        "Gonderildi: $KeyCode"
    } else {
        "Tus gonderilemedi: $KeyCode"
    }
    if ($script:longPress.Checked) { $script:longPress.Checked = $false }
}

function New-KeyButton(
    [string]$Text,
    [int]$KeyCode,
    [System.Drawing.Color]$BackColor = [System.Drawing.Color]::FromArgb(42, 57, 68),
    [System.Drawing.Color]$ForeColor = [System.Drawing.Color]::White
) {
    $button = New-Object System.Windows.Forms.Button
    $button.Text = $Text
    $button.Width = 86
    $button.Height = 42
    $button.Margin = New-Object System.Windows.Forms.Padding(4)
    $button.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
    $button.FlatAppearance.BorderSize = 1
    $button.FlatAppearance.BorderColor = [System.Drawing.Color]::FromArgb(93, 113, 128)
    $button.BackColor = $BackColor
    $button.ForeColor = $ForeColor
    $button.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 9)
    $capturedCode = $KeyCode
    $button.Add_Click({ Send-TvKey $capturedCode }.GetNewClosure())
    return $button
}

function New-Section([string]$Title) {
    $container = New-Object System.Windows.Forms.FlowLayoutPanel
    $container.FlowDirection = [System.Windows.Forms.FlowDirection]::LeftToRight
    $container.WrapContents = $true
    $container.AutoSize = $true
    $container.AutoSizeMode = [System.Windows.Forms.AutoSizeMode]::GrowAndShrink
    $container.Width = 310
    $container.Margin = New-Object System.Windows.Forms.Padding(10, 5, 10, 5)
    $label = New-Object System.Windows.Forms.Label
    $label.Text = $Title
    $label.Width = 300
    $label.Height = 23
    $label.ForeColor = [System.Drawing.Color]::FromArgb(51, 181, 229)
    $label.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 10)
    $container.Controls.Add($label)
    return $container
}

$form = New-Object System.Windows.Forms.Form
$form.Text = 'TVApp Emulator Remote'
$form.ClientSize = New-Object System.Drawing.Size(360, 770)
$form.MinimumSize = New-Object System.Drawing.Size(376, 620)
$form.BackColor = [System.Drawing.Color]::FromArgb(17, 24, 30)
$form.ForeColor = [System.Drawing.Color]::White
$form.Font = New-Object System.Drawing.Font('Segoe UI', 9)
$form.StartPosition = [System.Windows.Forms.FormStartPosition]::CenterScreen
$form.TopMost = $true
$form.KeyPreview = $true

$main = New-Object System.Windows.Forms.FlowLayoutPanel
$main.Dock = [System.Windows.Forms.DockStyle]::Fill
$main.FlowDirection = [System.Windows.Forms.FlowDirection]::TopDown
$main.WrapContents = $false
$main.AutoScroll = $true
$main.Padding = New-Object System.Windows.Forms.Padding(0, 10, 0, 10)
$form.Controls.Add($main)

$header = New-Object System.Windows.Forms.Panel
$header.Width = 320
$header.Height = 55
$header.Margin = New-Object System.Windows.Forms.Padding(10, 0, 10, 4)
$script:statusLabel = New-Object System.Windows.Forms.Label
$script:statusLabel.Location = New-Object System.Drawing.Point(8, 7)
$script:statusLabel.Size = New-Object System.Drawing.Size(220, 22)
$script:statusLabel.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 10)
$header.Controls.Add($script:statusLabel)
$refresh = New-Object System.Windows.Forms.Button
$refresh.Text = 'Yenile'
$refresh.Location = New-Object System.Drawing.Point(230, 3)
$refresh.Size = New-Object System.Drawing.Size(82, 31)
$refresh.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
$refresh.ForeColor = [System.Drawing.Color]::White
$refresh.Add_Click({ $script:deviceSerial = ''; Refresh-Device })
$header.Controls.Add($refresh)
$script:longPress = New-Object System.Windows.Forms.CheckBox
$script:longPress.Text = 'Sonraki tusa uzun bas'
$script:longPress.Location = New-Object System.Drawing.Point(8, 32)
$script:longPress.Size = New-Object System.Drawing.Size(190, 22)
$script:longPress.ForeColor = [System.Drawing.Color]::FromArgb(183, 187, 196)
$header.Controls.Add($script:longPress)
$main.Controls.Add($header)

$navSection = New-Section 'Gezinme'
$navGrid = New-Object System.Windows.Forms.TableLayoutPanel
$navGrid.ColumnCount = 3
$navGrid.RowCount = 3
$navGrid.Width = 290
$navGrid.Height = 142
$navGrid.Margin = New-Object System.Windows.Forms.Padding(5, 0, 5, 2)
$navGrid.Controls.Add((New-KeyButton 'BACK' 4), 0, 0)
$navGrid.Controls.Add((New-KeyButton 'UP' 19), 1, 0)
$navGrid.Controls.Add((New-KeyButton 'HOME' 3), 2, 0)
$navGrid.Controls.Add((New-KeyButton 'LEFT' 21), 0, 1)
$navGrid.Controls.Add((New-KeyButton 'OK' 23 ([System.Drawing.Color]::FromArgb(32, 96, 128))), 1, 1)
$navGrid.Controls.Add((New-KeyButton 'RIGHT' 22), 2, 1)
$navGrid.Controls.Add((New-KeyButton 'CH-' 167), 0, 2)
$navGrid.Controls.Add((New-KeyButton 'DOWN' 20), 1, 2)
$navGrid.Controls.Add((New-KeyButton 'CH+' 166), 2, 2)
$navSection.Controls.Add($navGrid)
$main.Controls.Add($navSection)

$tvSection = New-Section 'TV islevleri'
@(
    @('INFO', 165), @('GUIDE', 172), @('SETTINGS', 176),
    @('MENU', 82), @('INPUT', 178), @('LAST', 229)
) | ForEach-Object { $tvSection.Controls.Add((New-KeyButton $_[0] $_[1])) }
$main.Controls.Add($tvSection)

$colorSection = New-Section 'Renk tuslari'
$colorSection.Controls.Add((New-KeyButton 'KIRMIZI' 183 ([System.Drawing.Color]::FromArgb(185, 38, 55))))
$colorSection.Controls.Add((New-KeyButton 'YESIL' 184 ([System.Drawing.Color]::FromArgb(24, 143, 83))))
$colorSection.Controls.Add((New-KeyButton 'SARI' 185 ([System.Drawing.Color]::FromArgb(191, 148, 18)) ([System.Drawing.Color]::Black)))
$colorSection.Controls.Add((New-KeyButton 'MAVI' 186 ([System.Drawing.Color]::FromArgb(24, 104, 191))))
$main.Controls.Add($colorSection)

$mediaSection = New-Section 'Medya ve ses'
@(
    @('GERI SAR', 89), @('OYNAT/DUR', 85), @('ILERI SAR', 90),
    @('VOL-', 25), @('SESSIZ', 164), @('VOL+', 24)
) | ForEach-Object { $mediaSection.Controls.Add((New-KeyButton $_[0] $_[1])) }
$main.Controls.Add($mediaSection)

$numberSection = New-Section 'Numaralar'
1..9 | ForEach-Object { $numberSection.Controls.Add((New-KeyButton $_ (7 + $_))) }
$numberSection.Controls.Add((New-KeyButton '0' 7))
$main.Controls.Add($numberSection)

$hint = New-Object System.Windows.Forms.Label
$hint.Text = "Klavye: Yonler/Enter/Esc | F5-F8: Renkler | PageUp/PageDown: Kanal"
$hint.Width = 310
$hint.Height = 42
$hint.Margin = New-Object System.Windows.Forms.Padding(15, 6, 10, 8)
$hint.ForeColor = [System.Drawing.Color]::FromArgb(183, 187, 196)
$main.Controls.Add($hint)

$form.Add_KeyDown({
    param($sender, $event)
    $mapping = @{
        ([System.Windows.Forms.Keys]::Up) = 19
        ([System.Windows.Forms.Keys]::Down) = 20
        ([System.Windows.Forms.Keys]::Left) = 21
        ([System.Windows.Forms.Keys]::Right) = 22
        ([System.Windows.Forms.Keys]::Enter) = 23
        ([System.Windows.Forms.Keys]::Escape) = 4
        ([System.Windows.Forms.Keys]::PageUp) = 166
        ([System.Windows.Forms.Keys]::PageDown) = 167
        ([System.Windows.Forms.Keys]::F5) = 183
        ([System.Windows.Forms.Keys]::F6) = 184
        ([System.Windows.Forms.Keys]::F7) = 185
        ([System.Windows.Forms.Keys]::F8) = 186
    }
    if ($mapping.ContainsKey($event.KeyCode)) {
        Send-TvKey $mapping[$event.KeyCode]
        $event.Handled = $true
        $event.SuppressKeyPress = $true
    }
})

Refresh-Device
[void]$form.ShowDialog()
