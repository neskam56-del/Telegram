param(
    [Parameter(Mandatory = $true)][string]$ImagePath
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path $PSScriptRoot -Parent
$source = [System.Drawing.Bitmap]::FromFile($ImagePath)
try {
    $background = $source.GetPixel(0, 0)
    $sizes = @{ mdpi = 48; hdpi = 72; xhdpi = 96; xxhdpi = 144; xxxhdpi = 192 }
    foreach ($density in $sizes.Keys) {
        $size = $sizes[$density]
        foreach ($kind in @('normal', 'round', 'adaptive')) {
            $canvasSize = if ($kind -eq 'adaptive') { [int]($size * 108 / 48) } else { $size }
            $bitmap = New-Object System.Drawing.Bitmap($canvasSize, $canvasSize)
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
                $graphics.Clear([System.Drawing.Color]::Transparent)
                if ($kind -eq 'round') {
                    $clip = New-Object System.Drawing.Drawing2D.GraphicsPath
                    try {
                        $clip.AddEllipse(0, 0, $size, $size)
                        $graphics.SetClip($clip)
                    } finally {
                        $clip.Dispose()
                    }
                }
                $brush = New-Object System.Drawing.SolidBrush($background)
                try {
                    $graphics.FillRectangle($brush, 0, 0, $canvasSize, $canvasSize)
                } finally {
                    $brush.Dispose()
                }
                $imageSize = if ($kind -eq 'adaptive') { [int]($canvasSize * 66 / 108) } else { $size }
                $offset = [int](($canvasSize - $imageSize) / 2)
                $side = [Math]::Min($source.Width, $source.Height)
                $destination = New-Object System.Drawing.Rectangle($offset, $offset, $imageSize, $imageSize)
                $graphics.DrawImage($source, $destination, ($source.Width - $side) / 2, ($source.Height - $side) / 2,
                    $side, $side, [System.Drawing.GraphicsUnit]::Pixel)
                $name = switch ($kind) {
                    normal { 'ic_launcher.png' }
                    round { 'ic_launcher_round.png' }
                    adaptive { 'fgram_icon_foreground.png' }
                }
                $bitmap.Save((Join-Path $root "TMessagesProj/src/main/res/mipmap-$density/$name"), [System.Drawing.Imaging.ImageFormat]::Png)
                if ($kind -eq 'normal') {
                    $bitmap.Save((Join-Path $root "TMessagesProj_AppStandalone/src/main/res/mipmap-$density/ic_launcher_sa.png"), [System.Drawing.Imaging.ImageFormat]::Png)
                }
            } finally {
                $graphics.Dispose()
                $bitmap.Dispose()
            }
        }
    }
} finally {
    $source.Dispose()
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
Get-ChildItem (Join-Path $root 'TMessagesProj/src/main/res/values*/strings.xml') | ForEach-Object {
    $text = [System.IO.File]::ReadAllText($_.FullName)
    $text = [regex]::Replace($text, '(<string name="AppName(?:Beta)?">)[^<]*(</string>)', '${1}Fgram${2}')
    [System.IO.File]::WriteAllText($_.FullName, $text, $utf8)
}
