import Link from 'next/link';

export default function HomePage() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 dark:from-gray-900 dark:to-gray-800">
      <div className="container mx-auto px-4 py-16">
        {/* Header */}
        <header className="text-center mb-16">
          <h1 className="text-5xl font-bold text-gray-900 dark:text-white mb-4">
            Call Auditing Platform
          </h1>
          <p className="text-xl text-gray-600 dark:text-gray-300 max-w-2xl mx-auto">
            Voice of the Customer platform for analyzing call recordings,
            extracting insights, and ensuring compliance.
          </p>
        </header>

        {/* Status Card */}
        <div className="max-w-4xl mx-auto bg-white dark:bg-gray-800 rounded-lg shadow-lg p-8 mb-12">
          <div className="flex items-center gap-3 mb-6">
            <div className="h-3 w-3 bg-green-500 rounded-full animate-pulse"></div>
            <h2 className="text-2xl font-semibold text-gray-900 dark:text-white">
              System Status
            </h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="space-y-4">
              <h3 className="font-semibold text-gray-700 dark:text-gray-300">
                Frontend
              </h3>
              <ul className="space-y-2 text-sm">
                <li className="flex items-center gap-2">
                  <span className="text-green-500">✓</span>
                  <span className="text-gray-600 dark:text-gray-400">
                    Next.js 15 App Router
                  </span>
                </li>
                <li className="flex items-center gap-2">
                  <span className="text-green-500">✓</span>
                  <span className="text-gray-600 dark:text-gray-400">
                    React 19
                  </span>
                </li>
                <li className="flex items-center gap-2">
                  <span className="text-green-500">✓</span>
                  <span className="text-gray-600 dark:text-gray-400">
                    TypeScript 5.7 (Strict Mode)
                  </span>
                </li>
                <li className="flex items-center gap-2">
                  <span className="text-green-500">✓</span>
                  <span className="text-gray-600 dark:text-gray-400">
                    Tailwind CSS 4.0
                  </span>
                </li>
              </ul>
            </div>

            <div className="space-y-4">
              <h3 className="font-semibold text-gray-700 dark:text-gray-300">
                Backend Services
              </h3>
              <ul className="space-y-2 text-sm">
                <li className="flex items-center gap-2">
                  <span className="text-yellow-500">⏳</span>
                  <span className="text-gray-600 dark:text-gray-400">
                    Call Ingestion (Port 8081)
                  </span>
                </li>
                <li className="flex items-center gap-2">
                  <span className="text-yellow-500">⏳</span>
                  <span className="text-gray-600 dark:text-gray-400">
                    Transcription (Port 8082)
                  </span>
                </li>
                <li className="flex items-center gap-2">
                  <span className="text-yellow-500">⏳</span>
                  <span className="text-gray-600 dark:text-gray-400">
                    Analytics (Port 8086)
                  </span>
                </li>
                <li className="flex items-center gap-2">
                  <span className="text-yellow-500">⏳</span>
                  <span className="text-gray-600 dark:text-gray-400">
                    API Gateway (Port 8080)
                  </span>
                </li>
              </ul>
            </div>
          </div>

          <div className="mt-6 p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
            <p className="text-sm text-blue-800 dark:text-blue-200">
              <strong>Note:</strong> Backend services are part of the
              microservices architecture. Start them with{' '}
              <code className="px-1 py-0.5 bg-blue-100 dark:bg-blue-800 rounded font-mono text-xs">
                docker compose up
              </code>
            </p>
          </div>
        </div>

        {/* Quick Links */}
        <div className="max-w-4xl mx-auto grid grid-cols-1 md:grid-cols-3 gap-6">
          <Link
            href="/calls"
            className="group p-6 bg-white dark:bg-gray-800 rounded-lg shadow hover:shadow-xl transition-shadow"
          >
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2 group-hover:text-primary-600 dark:group-hover:text-primary-400">
              Call Management
            </h3>
            <p className="text-sm text-gray-600 dark:text-gray-400">
              Upload, view, and manage call recordings
            </p>
          </Link>

          <Link
            href="/analytics"
            className="group p-6 bg-white dark:bg-gray-800 rounded-lg shadow hover:shadow-xl transition-shadow"
          >
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2 group-hover:text-primary-600 dark:group-hover:text-primary-400">
              Analytics
            </h3>
            <p className="text-sm text-gray-600 dark:text-gray-400">
              View insights and sentiment analysis
            </p>
          </Link>

          <Link
            href="/voc"
            className="group p-6 bg-white dark:bg-gray-800 rounded-lg shadow hover:shadow-xl transition-shadow"
          >
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2 group-hover:text-primary-600 dark:group-hover:text-primary-400">
              Voice of Customer
            </h3>
            <p className="text-sm text-gray-600 dark:text-gray-400">
              Extract themes and customer insights
            </p>
          </Link>
        </div>

        {/* Footer */}
        <footer className="text-center mt-16 text-gray-500 dark:text-gray-400 text-sm">
          <p>
            Call Auditing Platform • Event-Driven Architecture • Powered by
            Spring Boot & FastAPI
          </p>
        </footer>
      </div>
    </div>
  );
}
