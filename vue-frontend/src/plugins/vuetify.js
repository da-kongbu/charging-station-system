import 'vuetify/styles'
import '@mdi/font/css/materialdesignicons.css'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'

const vuetify = createVuetify({
  components,
  directives,
  theme: {
    defaultTheme: 'light',
    themes: {
      light: {
        colors: {
          primary: '#00b894',
          'primary-dark': '#00a383',
          secondary: '#0984e3',
          danger: '#d63031',
          warning: '#fdcb6e',
          background: '#f5f6fa',
        }
      }
    }
  }
})

export default vuetify
