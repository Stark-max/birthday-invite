document.addEventListener("input", (event) => {
  const form = event.target.closest("[data-theme-form]");
  if (!form) return;
  const preview = document.querySelector(form.dataset.themeForm);
  if (!preview) return;
  const mappings = {
    colorBg: "--preview-bg",
    colorBgCard: "--preview-card",
    colorText: "--preview-text",
    colorAccent: "--preview-accent",
    borderRadius: "--preview-radius"
  };
  Object.entries(mappings).forEach(([name, cssVar]) => {
    const input = form.querySelector(`[name="${name}"]`);
    if (!input || !input.value) return;
    preview.style.setProperty(cssVar, name === "borderRadius" ? `${input.value}px` : input.value);
  });
});
