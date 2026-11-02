"""Frame a complete video and export configurable MP4/GIF previews with FFmpeg."""

import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile

from PIL import Image, ImageDraw


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def frame_opening(frame):
    """Find the transparent screen opening containing the frame's center."""
    opening = frame.getchannel("A").point(lambda alpha: 255 if alpha > 128 else 0)
    center = (frame.width // 2, frame.height // 2)
    if opening.getpixel(center) != 0:
        raise ValueError("The frame center must be inside its transparent screen opening")
    ImageDraw.floodfill(opening, center, 128)
    opening = opening.point(lambda alpha: 255 if alpha == 128 else 0)
    box = opening.getbbox()
    if box[0] == 0 or box[1] == 0 or box[2] == frame.width or box[3] == frame.height:
        raise ValueError("The transparent screen opening must be enclosed by the frame")
    return opening, box


def publish(source, destination):
    """Replace a preview after copying it to the destination filesystem."""
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False,
                                     suffix=destination.suffix) as temporary:
        pending = Path(temporary.name)
    try:
        shutil.copyfile(source, pending)
        pending.replace(destination)
    finally:
        pending.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--frame", type=Path, required=True)
    parser.add_argument("--mp4", type=Path, required=True)
    parser.add_argument("--gif", type=Path)
    parser.add_argument("--video-height", type=int, default=1200)
    parser.add_argument("--gif-height", type=int, default=600)
    parser.add_argument("--video-fps", type=int, default=30)
    parser.add_argument("--gif-fps", type=int, default=20)
    parser.add_argument("--crf", type=int, default=20)
    parser.add_argument("--colors", type=int, default=256)
    args = parser.parse_args()
    if min(args.video_height, args.gif_height, args.video_fps, args.gif_fps) <= 0:
        parser.error("Dimensions and frame rates must be positive")
    if args.video_height % 2 or not 0 <= args.crf <= 51 or not 4 <= args.colors <= 256:
        parser.error("Video height must be even; CRF must be 0-51; colors must be 4-256")

    frame = Image.open(args.frame).convert("RGBA")
    opening, (x, y, right, bottom) = frame_opening(frame)
    width, height = right - x, bottom - y
    video_width = max(2, 2 * round(frame.width * args.video_height / frame.height / 2))
    gif_width = max(1, round(frame.width * args.gif_height / frame.height))

    with tempfile.TemporaryDirectory(prefix="framed-preview-") as temp:
        temp = Path(temp)
        mask = temp / "opening-mask.png"
        opening.crop((x, y, right, bottom)).save(mask)
        graph = (
            f"[0:v]setpts=PTS-STARTPTS,fps={args.video_fps},"
            f"scale={width}:{height}:force_original_aspect_ratio=decrease,"
            f"pad={width}:{height}:(ow-iw)/2:(oh-ih)/2:black,format=rgba[v];"
            "[1:v]format=gray[m];[v][m]alphamerge=shortest=1[screen];"
            f"color=c=0x0b0d19:s={frame.width}x{frame.height}:r={args.video_fps}[bg];"
            f"[bg][screen]overlay={x}:{y}:shortest=1[phone];"
            "[phone][2:v]overlay=0:0:shortest=1,"
            f"scale={video_width}:{args.video_height}:flags=lanczos,"
            "setsar=1,format=yuv420p[out]"
        )
        mp4, gif = temp / "preview.mp4", temp / "preview.gif"
        run("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
            "-i", args.input, "-loop", 1, "-i", mask,
            "-loop", 1, "-i", args.frame, "-filter_complex", graph,
            "-map", "[out]", "-an", "-c:v", "libx264", "-preset", "slow",
            "-crf", args.crf, "-movflags", "+faststart", mp4)
        if args.gif:
            run("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", mp4,
                "-filter_complex",
                f"fps={args.gif_fps},scale={gif_width}:{args.gif_height}:flags=lanczos,"
                f"split[a][b];[a]palettegen=max_colors={args.colors}:stats_mode=full[p];"
                "[b][p]paletteuse=dither=none:diff_mode=rectangle", "-loop", 0, gif)
        publish(mp4, args.mp4)
        if args.gif:
            publish(gif, args.gif)


if __name__ == "__main__":
    main()
