const wheelRotations = {};

function drawWheel(canvas, segments) {
  const ctx = canvas.getContext("2d");
  const size = canvas.width = canvas.height = 480;
  const radius = size / 2 - 12;
  const center = size / 2;
  ctx.clearRect(0, 0, size, size);

  if (!segments.length) {
    ctx.fillStyle = "#f7efe2";
    ctx.beginPath();
    ctx.arc(center, center, radius, 0, Math.PI * 2);
    ctx.fill();
    return;
  }

  segments.forEach((segment, index) => {
    const start = (index / segments.length) * Math.PI * 2;
    const end = ((index + 1) / segments.length) * Math.PI * 2;
    ctx.beginPath();
    ctx.moveTo(center, center);
    ctx.arc(center, center, radius, start, end);
    ctx.closePath();
    ctx.fillStyle = segment.color || ["#ff6b8a", "#ffd166", "#5db7ff", "#7d9b76", "#9b7dc0"][index % 5];
    ctx.fill();
    ctx.strokeStyle = "rgba(255,255,255,0.72)";
    ctx.lineWidth = 4;
    ctx.stroke();

    ctx.save();
    ctx.translate(center, center);
    ctx.rotate(start + (end - start) / 2);
    ctx.textAlign = "left";
    ctx.textBaseline = "middle";
    ctx.fillStyle = "#fff";
    ctx.font = "700 18px sans-serif";
    wrapWheelText(ctx, segment.text || "", 50, 0, radius - 68, 22);
    ctx.restore();
  });

  ctx.beginPath();
  ctx.arc(center, center, 48, 0, Math.PI * 2);
  ctx.fillStyle = "#fff8ea";
  ctx.fill();
  ctx.strokeStyle = "rgba(44,40,37,0.18)";
  ctx.lineWidth = 4;
  ctx.stroke();
}

function wrapWheelText(ctx, text, x, y, maxWidth, lineHeight) {
  const words = String(text).split(" ");
  const lines = [];
  let line = "";
  words.forEach((word) => {
    const testLine = line ? `${line} ${word}` : word;
    if (ctx.measureText(testLine).width > maxWidth && line) {
      lines.push(line);
      line = word;
    } else {
      line = testLine;
    }
  });
  if (line) lines.push(line);

  const startY = y - ((lines.length - 1) * lineHeight) / 2;
  lines.slice(0, 3).forEach((item, index) => {
    ctx.fillText(item, x, startY + index * lineHeight);
  });
}

async function spinWheel(instanceId, guestId) {
  const config = JSON.parse(document.getElementById(`activity-config-${instanceId}`).textContent);
  const canvas = document.getElementById(`wheel-${instanceId}`);
  const output = document.getElementById(`wheel-result-${instanceId}`);
  const button = document.getElementById(`wheel-button-${instanceId}`);
  const segments = config.segments || [];
  drawWheel(canvas, segments);
  output.textContent = "Колесо набирает ход...";
  button.disabled = true;
  canvas.classList.add("spinning");

  try {
    const response = await fetch(`/activities/${instanceId}/play`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ guestId })
    });
    const result = await response.json();
    if (result.success && result.data && Number.isInteger(result.data.segmentIndex)) {
      await stopWheelAt(canvas, segments.length, result.data.segmentIndex, instanceId);
    }
    output.textContent = result.message;
  } catch (error) {
    output.textContent = "Не получилось прокрутить колесо.";
  } finally {
    canvas.classList.remove("spinning");
    button.disabled = false;
  }
}

function stopWheelAt(canvas, segmentCount, selectedIndex, instanceId) {
  if (!segmentCount) return Promise.resolve();
  const slice = 360 / segmentCount;
  const selectedCenter = selectedIndex * slice + slice / 2;
  const targetAtTop = 270 - selectedCenter;
  const previous = wheelRotations[instanceId] || 0;
  const next = previous + 2160 + normalizeDegrees(targetAtTop - previous);
  wheelRotations[instanceId] = next;
  canvas.style.transition = "none";
  canvas.style.transform = `rotate(${previous}deg)`;
  void canvas.offsetWidth;
  canvas.style.transition = "transform 5.2s cubic-bezier(0.12, 0.64, 0.16, 1)";

  return new Promise((resolve) => {
    let completed = false;
    const finish = () => {
      if (completed) return;
      completed = true;
      canvas.removeEventListener("transitionend", finish);
      resolve();
    };
    canvas.addEventListener("transitionend", finish, { once: true });
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        canvas.style.transform = `rotate(${next}deg)`;
      });
    });
    setTimeout(finish, 5600);
  });
}

function normalizeDegrees(value) {
  return ((value % 360) + 360) % 360;
}

document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("[data-wheel-config]").forEach((el) => {
    const config = JSON.parse(el.textContent);
    const canvas = document.getElementById(el.dataset.wheelConfig);
    if (canvas) drawWheel(canvas, config.segments || []);
  });
});
