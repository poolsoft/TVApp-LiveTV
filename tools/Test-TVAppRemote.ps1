$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms

$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $PSScriptRoot 'TVAppRemote.ps1'), [ref]$tokens, [ref]$parseErrors
)
if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
$ast.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
        $node.Name -in @('Refresh-Device', 'Update-DeviceStatus', 'Send-TvKey')
}, $false) | ForEach-Object { Invoke-Expression $_.Extent.Text }

function Assert-Equal($Expected, $Actual) {
    if ($Expected -ne $Actual) { throw "Expected '$Expected', got '$Actual'" }
}

$script:connectedDevices = @('emulator-5554', 'emulator-5558')
$script:sentKeys = @()
function Test-Adb {
    $global:LASTEXITCODE = 0
    if ($args[0] -eq 'devices') {
        'List of devices attached'
        $script:connectedDevices | ForEach-Object { "$_`tdevice" }
        "emulator-5560`tunauthorized"
    } elseif ($args[2] -eq 'emu') {
        "AVD-$($args[1])"
        'OK'
    } else {
        $script:sentKeys += ,@($args)
    }
}

$script:adb = 'Test-Adb'
$script:deviceSerial = ''
$script:devicePicker = New-Object System.Windows.Forms.ComboBox
$script:devicePicker.DisplayMember = 'Label'
$script:statusLabel = New-Object System.Windows.Forms.Label
$script:longPress = New-Object System.Windows.Forms.CheckBox
try {
    Refresh-Device
    Assert-Equal 2 $script:devicePicker.Items.Count
    Assert-Equal '' $script:deviceSerial
    Assert-Equal 'AVD-emulator-5558 (emulator-5558)' $script:devicePicker.Items[1].Label

    $script:deviceSerial = 'emulator-5558'
    Refresh-Device
    Assert-Equal 'emulator-5558' $script:devicePicker.SelectedItem.Serial
    Send-TvKey 23
    Assert-Equal 'emulator-5558' $script:sentKeys[0][1]
    Assert-Equal 23 $script:sentKeys[0][-1]

    $script:connectedDevices = @('emulator-5554')
    Refresh-Device
    Assert-Equal '' $script:deviceSerial
    Send-TvKey 19
    Assert-Equal 1 $script:sentKeys.Count

    Refresh-Device
    Assert-Equal 'emulator-5554' $script:deviceSerial
    $script:longPress.Checked = $true
    Send-TvKey 22
    Assert-Equal '--longpress' $script:sentKeys[1][5]
    Assert-Equal $false $script:longPress.Checked
    'PASS: emulator selection, refresh, disconnect and key routing'
} finally {
    $script:devicePicker.Dispose()
    $script:statusLabel.Dispose()
    $script:longPress.Dispose()
}
