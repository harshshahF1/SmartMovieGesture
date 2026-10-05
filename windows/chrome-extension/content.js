function applyCommands(commands) {
  const video = document.querySelector("video");
  if (!video) return;

  for (const command of commands || []) {
    if (command === "play") video.play().catch(() => {});
    if (command === "pause") video.pause();
    if (command === "rewind") video.currentTime = Math.max(0, video.currentTime - 5);
    if (command === "forward") {
      video.currentTime = Math.min(
        Number.isFinite(video.duration) ? video.duration : Infinity,
        video.currentTime + 5
      );
    }
  }
}

function pollController() {
  chrome.runtime.sendMessage({ type: "poll_commands" }, (response) => {
    if (chrome.runtime.lastError) return;
    if (response?.ok) applyCommands(response.commands);
  });
}

setInterval(pollController, 250);
pollController();
