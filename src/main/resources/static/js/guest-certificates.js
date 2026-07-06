function printGuestCertificate() {
  window.print();
}

function shareGuestCertificate() {
  if (!navigator.share) {
    navigator.clipboard?.writeText(window.location.href);
    return;
  }
  navigator.share({
    title: document.title,
    url: window.location.href
  }).catch(() => {});
}
