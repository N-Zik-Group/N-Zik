# Framed video previews

Use `convert-framed-preview.py` to place a transparent phone frame over a video
and configure the size, quality and frame rate of its MP4 and optional GIF.
The complete source is preserved at original speed, without cuts. Audio is
omitted. These exports belong in README/site previews, not Fastlane screenshots.

## Requirements

- FFmpeg on PATH.
- Python with Pillow installed.
- A transparent PNG frame with an enclosed screen opening containing its center.

## Usage

From the mobile repository root:

```powershell
python assets/design/preview/convert-framed-preview.py --input "$HOME/Downloads/video.mp4" --frame "assets/design/X17Ultra Frame1.png" --mp4 "../N-Zik-Website/videos/glass-demo.mp4" --gif "assets/design/preview/framed_videos/glass-demo.gif"
```

Choose the input, frame and output paths for any new preview. Omit `--gif` for
MP4 only. For a worktree, adjust the website path to its actual location.
Both conversions finish before the published previews are replaced.

| Option | Default | Effect |
| --- | ---: | --- |
| `--video-height` | 1200 | MP4 height; width follows the frame proportions |
| `--gif-height` | 600 | GIF height; lower values reduce weight and detail |
| `--video-fps` | 30 | MP4 fluidity |
| `--gif-fps` | 20 | GIF fluidity; reducing it does not accelerate playback |
| `--crf` | 20 | H.264 quality: lower is sharper/heavier, higher is smaller |
| `--colors` | 256 | GIF palette size; fewer colors can flatten gradients |

The opening is detected from the frame's transparency. The video fits inside
it without stretching, with padding if necessary. Rounded corners and camera
cutouts remain masked, then the original frame is overlaid. GIF palette
quantization can create color bands; MP4 is the higher-fidelity format.

## Verification

Compare the output duration against the source's video stream, allowing one
output frame for timestamp rounding. Check several moments including the end,
then compare frame margins, corners and camera position against existing
previews at the same size. A different number of frames at a different frame
rate must not change the playback speed.

The Glass exports are approximately 151.67 seconds: MP4 at 574 x 1200/30 fps
(23.35 MB), GIF at 287 x 600/20 fps (60.18 MB). Their frame aligns with the
existing previews. The four Glass store captures are separate opaque PNGs
under `fastlane/metadata/android/en-US/images/phoneScreenshots/`; no video or GIF
is added to Fastlane.


## Still-image transparency

Keep the supplied screenshots at their native dimensions with their original
alpha channel. Do not add a second frame, dark matte or padded canvas.
Fastlane receives RGBA PNGs; website and README previews receive WebP with the
same dimensions and alpha. For the supplied Glass captures, all are1282x2681.
The Glass screenshots are grouped as04(settings),05(home),06(player) and07(carousel) in both mobile previews and Fastlane.
PNG output pixels and WebP alpha were compared against their source images.
