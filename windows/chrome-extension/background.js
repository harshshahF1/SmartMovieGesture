const SERVER = "http://127.0.0.1:8765/commands";

async function pollCommands() {
  try {
    const response = await fetch(SERVER, {
      method: "GET",
      cache: "no-store"
    });

    if (!response.ok) {
      return { ok: false, commands: [] };
    }

    const data = await response.json();
    return {
      ok: true,
      commands: Array.isArray(data.commands) ? data.commands : []
    };
  } catch (error) {
    return { ok: false, commands: [] };
  }
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type !== "poll_commands") return;

  pollCommands().then(sendResponse);
  return true;
});
