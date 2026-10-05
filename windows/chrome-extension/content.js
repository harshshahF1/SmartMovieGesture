const SERVER = "http://127.0.0.1:8765/commands";

async function poll() {
  try {
    const response = await fetch(SERVER, { cache: "no-store" });
    if (!response.ok) return;
    const data = await response.json();
    const video = document.querySelector("video");
    if (!video) return;

    for (const command of data.commands || []) {
      if (command === "play") video.play().catch(() => {});
      if (command === "pause") video.pause();
      if (command === "rewind") video.currentTime = Math.max(0, video.currentTime - 5);
      if (command === "forward") video.currentTime = Math.min(video.duration || Infinity, video.currentTime + 5);
    }
  } catch (_) {}
}

setInterval(poll, 250);
poll();
