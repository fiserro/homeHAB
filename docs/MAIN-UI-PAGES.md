# OpenHAB Main UI Pages Guide

This document describes how to create and manage pages in OpenHAB Main UI.

## Basic UI vs Main UI - Key Differences

| Aspect | Basic UI | Main UI (Pages) |
|--------|----------|-----------------|
| Configuration | `.sitemap` files in `conf/sitemaps/` | JSON in `userdata/jsondb/uicomponents_ui_page.json` |
| URL | `/basicui/app` | `/` (default) |
| Customization | Limited, text-based | Rich, visual editor + YAML/JSON |
| Widgets | Fixed set | Extensive library + custom widgets |

**Important**: Pages are ONLY for Main UI. They cannot be used with Basic UI.

## Page Storage Location

Pages are stored in the JSON database:
```
openhab-dev/userdata/jsondb/uicomponents_ui_page.json
```

## JSON Structure

### Complete Page Example

```json
{
  "my-page-id": {
    "class": "org.openhab.core.ui.components.RootUIComponent",
    "value": {
      "uid": "my-page-id",
      "tags": [],
      "props": {
        "parameters": [],
        "parameterGroups": []
      },
      "timestamp": "Dec 18, 2025, 12:00:00 AM",
      "component": "oh-layout-page",
      "config": {
        "label": "My Page Title",
        "sidebar": true,
        "order": 10,
        "icon": "f7:house"
      },
      "slots": {
        "default": [
          {
            "component": "oh-block",
            "config": {
              "title": "Block Title"
            },
            "slots": {
              "default": [
                {
                  "component": "oh-grid-row",
                  "config": {},
                  "slots": {
                    "default": [
                      {
                        "component": "oh-grid-col",
                        "config": {},
                        "slots": {
                          "default": [
                            {
                              "component": "oh-label-card",
                              "config": {
                                "title": "Hello World!",
                                "label": "Some description",
                                "icon": "f7:hand_wave"
                              }
                            }
                          ]
                        }
                      }
                    ]
                  }
                }
              ]
            }
          }
        ]
      }
    }
  }
}
```

## Page Config Properties

| Property | Type | Description |
|----------|------|-------------|
| `label` | string | Page title displayed in sidebar and header |
| `sidebar` | boolean | `true` = show in sidebar menu |
| `order` | integer | Position in sidebar (lower = higher in menu, default 1000) |
| `icon` | string | Icon name (e.g., `f7:house`, `f7:wind`) |
| `visibleTo` | array | Restrict visibility: `["role:administrator", "user:john"]` |
| `hideNavbar` | boolean | Hide top navigation bar |
| `hideSidebarIcon` | boolean | Hide sidebar toggle icon |

## Widget Hierarchy

Layout pages use a nested structure:

```
oh-layout-page
└── oh-block                    # Section with optional title
    └── oh-grid-row             # Row container
        └── oh-grid-col         # Column (width: 1-100)
            └── [widget]        # Content widget
```

### Common Layout Widgets

| Widget | Description |
|--------|-------------|
| `oh-block` | Section container with optional title |
| `oh-grid-row` | Horizontal row container |
| `oh-grid-col` | Column with responsive width |
| `oh-masonry` | Auto-arranging grid layout |

### Common Content Widgets

| Widget | Description |
|--------|-------------|
| `oh-label-card` | Simple card with title/label |
| `oh-list-card` | Card containing a list of items |
| `oh-toggle-item` | Switch/toggle control |
| `oh-slider-item` | Slider control |
| `oh-stepper-item` | Numeric stepper (+/-) |
| `oh-label-item` | Read-only label in list |
| `oh-input-item` | Text/number input |

## Responsive Column Widths

The `oh-grid-col` component supports responsive breakpoints:

```json
{
  "component": "oh-grid-col",
  "config": {
    "width": "100",      // Default (smallest screens)
    "xsmall": "100",     // >= 480px
    "small": "50",       // >= 568px
    "medium": "33",      // >= 768px
    "large": "25",       // >= 1024px
    "xlarge": "20"       // >= 1200px
  }
}
```

## Creating Pages

### Method 1: Source File + Deploy Script (Recommended)

Pages are stored in a version-controlled source file and deployed via the deploy script:

**Source file:** `openhab-dev/conf/ui-pages.json`

**Deployment:**
```bash
./deploy.sh dev   # Copies to userdata/jsondb/uicomponents_ui_page.json
```

This approach:
- Keeps pages in git version control
- Follows the same workflow as JAR deployment
- Allows easy rollback to previous versions

### Method 2: Main UI Editor

1. Open http://localhost:8888
2. Go to **Settings → Pages**
3. Click **+** to create new page
4. Choose layout type (Responsive recommended)
5. Use **Design** tab for visual editing or **Code** tab for YAML
6. In page settings, enable **Show on Sidebar**

### Method 3: REST API

Requires authentication (API token):

```bash
curl -X PUT "http://localhost:8888/rest/ui/components/ui:page/my-page" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_TOKEN" \
  -d @page.json
```

## YAML Format (Code Tab)

When editing in Main UI Code tab, use YAML format:

```yaml
config:
  label: My Page
  sidebar: true
  order: 10
blocks:
  - component: oh-block
    config:
      title: Block Title
    slots:
      default:
        - component: oh-grid-row
          config: {}
          slots:
            default:
              - component: oh-grid-col
                config:
                  width: "100"
                slots:
                  default:
                    - component: oh-label-card
                      config:
                        title: Hello!
                        icon: f7:hand_wave
```

## Icon Reference

Icons use Framework7 icon set with `f7:` prefix:

- `f7:house` - Home
- `f7:wind` - Wind/ventilation
- `f7:thermometer` - Temperature
- `f7:drop` - Humidity/water
- `f7:bolt` - Power/electricity
- `f7:gauge` - Gauge/meter
- `f7:switch_2` - Switch
- `f7:timer` - Timer/clock
- `f7:hand_wave` - Wave/greeting

Full list: https://framework7.io/icons/

## Verifying Pages

### Check registered pages:
```bash
curl -s http://localhost:8888/rest/ui/components/ui:page | jq '.[].uid'
```

### Check page details:
```bash
curl -s http://localhost:8888/rest/ui/components/ui:page/my-page | jq .
```

### Check sidebar visibility:
```bash
curl -s http://localhost:8888/rest/ui/components/ui:page | \
  jq '.[] | {uid: .uid, sidebar: .config.sidebar, label: .config.label}'
```

## Troubleshooting

### Page not showing in sidebar

1. Verify `config.sidebar: true` is set
2. Hard refresh browser: `Ctrl+Shift+R`
3. Check if logged in (sidebar pages require authentication)
4. Verify page loads via REST API

### Page shows but is empty

1. Check `slots.default` contains widgets
2. Verify widget hierarchy is correct
3. Check browser console for JavaScript errors

### Changes not applying

1. Restart OpenHAB: `docker-compose restart openhab`
2. Wait for full startup (~30 seconds)
3. Hard refresh browser

## References

- [Layout Pages Documentation](https://www.openhab.org/docs/ui/layout-pages.html)
- [Building Pages](https://www.openhab.org/docs/ui/building-pages.html)
- [Widget Expressions](https://www.openhab.org/docs/ui/widget-expressions-variables.html)
- [Community Examples](https://community.openhab.org/t/oh3-main-ui-examples/117928)
- [Framework7 Icons](https://framework7.io/icons/)
