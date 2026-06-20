import * as pdfjsLib from 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/4.0.379/pdf.min.mjs';
pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/4.0.379/pdf.worker.min.mjs';

const originalPageSizes = {};

// Listen for selection changes and post them to Kotlin
document.addEventListener('selectionchange', () => {
  const selection = window.getSelection();
  if (!selection.isCollapsed) {
    const textStr = selection.toString();
    const range = selection.getRangeAt(0);
    const rect = range.getBoundingClientRect(); // relative to the viewport
    if (window.Android && window.Android.onTextSelected) {
      window.Android.onTextSelected(
        textStr,
        rect.left,
        rect.top,
        rect.width,
        rect.height
      );
    }
  } else {
    if (window.Android && window.Android.onTextSelected) {
      window.Android.onTextSelected("", 0, 0, 0, 0);
    }
  }
});

// Expose updatePagePositions function to Kotlin
window.updatePagePositions = function(pagesInfo) {
  pagesInfo.forEach(info => {
    const el = document.getElementById('page-' + info.index);
    if (el) {
      const origW = originalPageSizes[info.index]?.width || 1;
      // Calculate dynamic scale factor comparing current layout width to unscaled width in points
      const scaleFactor = info.w / origW;
      
      el.style.transform = `translate3d(${info.x}px, ${info.y}px, 0) scale(${scaleFactor})`;
    }
  });
};

// Start loading the PDF document from Android secure source endpoint
async function initPdfJs() {
  try {
    const container = document.getElementById('container');
    const loadingTask = pdfjsLib.getDocument({ url: 'https://localpdf/document.pdf' });
    const pdf = await loadingTask.promise;
    const totalPages = pdf.numPages;

    if (window.Android && window.Android.onLoadSuccess) {
      window.Android.onLoadSuccess(totalPages);
    }

    // Render transparent textLayer for all pages
    for (let pageNum = 1; pageNum <= totalPages; pageNum++) {
      const pageIdx = pageNum - 1;
      const page = await pdf.getPage(pageNum);
      
      // Load viewport at native points resolution (scale = 1.0)
      const viewport = page.getViewport({ scale: 1.0 });
      originalPageSizes[pageIdx] = { width: viewport.width, height: viewport.height };

      const pageContainer = document.createElement('div');
      pageContainer.className = 'page-container';
      pageContainer.id = 'page-' + pageIdx;
      pageContainer.style.width = viewport.width + 'px';
      pageContainer.style.height = viewport.height + 'px';
      
      const textContent = await page.getTextContent();
      const textLayerDiv = document.createElement('div');
      textLayerDiv.className = 'textLayer';
      textLayerDiv.style.width = '100%';
      textLayerDiv.style.height = '100%';

      textContent.items.forEach(function (textItem) {
        const tx = pdfjsLib.Util.transform(
          viewport.transform,
          textItem.transform
        );
        const span = document.createElement('span');
        span.textContent = textItem.str;
        span.style.fontFamily = textItem.fontName;
        
        const fontSize = Math.sqrt(tx[0]*tx[0] + tx[1]*tx[1]);
        span.style.fontSize = fontSize + 'px';
        
        span.style.left = tx[4] + 'px';
        span.style.top = (viewport.height - tx[5] - fontSize) + 'px';
        
        const itemWidth = textItem.width;
        if (itemWidth > 0) {
          span.style.width = itemWidth + 'px';
        }
        
        textLayerDiv.appendChild(span);
      });

      pageContainer.appendChild(textLayerDiv);
      container.appendChild(pageContainer);
    }
  } catch (error) {
    console.error("PDF.js Overlays failed:", error);
  }
}

// Start
initPdfJs();
