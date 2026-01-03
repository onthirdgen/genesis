import { Header } from '@/components/layout/header';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Phone, TrendingUp, AlertCircle, CheckCircle2 } from 'lucide-react';

export default function DashboardPage() {
  // TODO: Replace with real data from API
  const stats = [
    {
      name: 'Total Calls',
      value: '1,234',
      change: '+12.5%',
      changeType: 'positive' as const,
      icon: Phone,
    },
    {
      name: 'Avg Sentiment Score',
      value: '7.8/10',
      change: '+0.3',
      changeType: 'positive' as const,
      icon: TrendingUp,
    },
    {
      name: 'Compliance Rate',
      value: '94.2%',
      change: '+2.1%',
      changeType: 'positive' as const,
      icon: CheckCircle2,
    },
    {
      name: 'Issues Flagged',
      value: '23',
      change: '-5',
      changeType: 'negative' as const,
      icon: AlertCircle,
    },
  ];

  return (
    <div>
      <Header
        title="Dashboard"
        description="Overview of call auditing metrics and insights"
      />

      <div className="p-6 space-y-6">
        {/* Stats Grid */}
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          {stats.map((stat) => {
            const Icon = stat.icon;
            return (
              <Card key={stat.name}>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {stat.name}
                  </CardTitle>
                  <Icon className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{stat.value}</div>
                  <p
                    className={`text-xs ${
                      stat.changeType === 'positive'
                        ? 'text-green-600'
                        : 'text-red-600'
                    }`}
                  >
                    {stat.change} from last month
                  </p>
                </CardContent>
              </Card>
            );
          })}
        </div>

        {/* Recent Activity */}
        <div className="grid gap-4 md:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Recent Calls</CardTitle>
              <CardDescription>
                Latest uploaded calls awaiting processing
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                {[1, 2, 3].map((i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between p-3 border rounded-lg"
                  >
                    <div className="flex items-center gap-3">
                      <div className="h-10 w-10 rounded-full bg-gray-200 flex items-center justify-center">
                        <Phone className="h-5 w-5 text-gray-600" />
                      </div>
                      <div>
                        <p className="text-sm font-medium">Call #{1230 + i}</p>
                        <p className="text-xs text-muted-foreground">
                          2 minutes ago
                        </p>
                      </div>
                    </div>
                    <span className="px-2 py-1 text-xs font-medium bg-yellow-100 text-yellow-800 rounded">
                      Processing
                    </span>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Compliance Alerts</CardTitle>
              <CardDescription>
                Issues requiring review or action
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                {[
                  { severity: 'HIGH', message: 'Missing compliance phrases' },
                  { severity: 'MEDIUM', message: 'Sentiment drop detected' },
                  { severity: 'LOW', message: 'Call duration exceeded' },
                ].map((alert, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between p-3 border rounded-lg"
                  >
                    <div className="flex items-center gap-3">
                      <AlertCircle
                        className={`h-5 w-5 ${
                          alert.severity === 'HIGH'
                            ? 'text-red-600'
                            : alert.severity === 'MEDIUM'
                            ? 'text-yellow-600'
                            : 'text-blue-600'
                        }`}
                      />
                      <div>
                        <p className="text-sm font-medium">{alert.message}</p>
                        <p className="text-xs text-muted-foreground">
                          Call #{1230 + i}
                        </p>
                      </div>
                    </div>
                    <span
                      className={`px-2 py-1 text-xs font-medium rounded ${
                        alert.severity === 'HIGH'
                          ? 'bg-red-100 text-red-800'
                          : alert.severity === 'MEDIUM'
                          ? 'bg-yellow-100 text-yellow-800'
                          : 'bg-blue-100 text-blue-800'
                      }`}
                    >
                      {alert.severity}
                    </span>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>

        {/* System Status */}
        <Card>
          <CardHeader>
            <CardTitle>Backend Services Status</CardTitle>
            <CardDescription>
              Current status of microservices
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid gap-4 md:grid-cols-3">
              {[
                { name: 'Call Ingestion', status: 'operational', port: 8081 },
                { name: 'Transcription', status: 'operational', port: 8082 },
                { name: 'Sentiment Analysis', status: 'operational', port: 8083 },
                { name: 'VoC Service', status: 'operational', port: 8084 },
                { name: 'Audit Service', status: 'operational', port: 8085 },
                { name: 'Analytics', status: 'operational', port: 8086 },
              ].map((service) => (
                <div
                  key={service.name}
                  className="flex items-center justify-between p-3 border rounded-lg"
                >
                  <div>
                    <p className="text-sm font-medium">{service.name}</p>
                    <p className="text-xs text-muted-foreground">
                      Port {service.port}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="h-2 w-2 rounded-full bg-green-500" />
                    <span className="text-xs text-muted-foreground">
                      {service.status}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
