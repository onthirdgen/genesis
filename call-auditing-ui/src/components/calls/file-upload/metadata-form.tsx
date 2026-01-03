/**
 * Metadata Form Component
 *
 * Editable form for call metadata with auto-detection indicators
 */

import { useState, useEffect } from 'react';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { cn } from '@/lib/utils/cn';
import type { CallMetadata } from '@/lib/types/call';
import { Sparkles } from 'lucide-react';

export interface MetadataFormProps {
  metadata: CallMetadata;
  onChange: (metadata: CallMetadata) => void;
  className?: string;
}

export function MetadataForm({
  metadata,
  onChange,
  className,
}: MetadataFormProps) {
  const [formData, setFormData] = useState<CallMetadata>(metadata);

  useEffect(() => {
    setFormData(metadata);
  }, [metadata]);

  const handleChange = (field: keyof CallMetadata, value: string) => {
    const updated = { ...formData, [field]: value };
    setFormData(updated);
    onChange(updated);
  };

  const isAutoDetected = (field: keyof CallMetadata): boolean => {
    return formData.autoDetected?.[field as keyof typeof formData.autoDetected] || false;
  };

  return (
    <Card className={cn('p-6', className)}>
      <div className="space-y-4">
        <div>
          <h3 className="text-sm font-semibold mb-3">Call Metadata</h3>
          <p className="text-xs text-muted-foreground">
            Fields marked with <Sparkles className="inline h-3 w-3" /> were auto-detected
          </p>
        </div>

        {/* Caller ID */}
        <div className="space-y-2">
          <Label htmlFor="callerId" className="flex items-center gap-2">
            Caller ID
            {isAutoDetected('callerId') && (
              <Badge variant="secondary" size="sm" className="gap-1">
                <Sparkles className="h-3 w-3" />
                Auto
              </Badge>
            )}
          </Label>
          <Input
            id="callerId"
            type="text"
            placeholder="e.g., +1-555-0123"
            value={formData.callerId || ''}
            onChange={(e) => handleChange('callerId', e.target.value)}
          />
        </div>

        {/* Agent ID */}
        <div className="space-y-2">
          <Label htmlFor="agentId" className="flex items-center gap-2">
            Agent ID
            {isAutoDetected('agentId') && (
              <Badge variant="secondary" size="sm" className="gap-1">
                <Sparkles className="h-3 w-3" />
                Auto
              </Badge>
            )}
          </Label>
          <Input
            id="agentId"
            type="text"
            placeholder="e.g., agent-001"
            value={formData.agentId || ''}
            onChange={(e) => handleChange('agentId', e.target.value)}
          />
        </div>

        {/* Customer ID */}
        <div className="space-y-2">
          <Label htmlFor="customerId" className="flex items-center gap-2">
            Customer ID
            {isAutoDetected('customerId') && (
              <Badge variant="secondary" size="sm" className="gap-1">
                <Sparkles className="h-3 w-3" />
                Auto
              </Badge>
            )}
          </Label>
          <Input
            id="customerId"
            type="text"
            placeholder="e.g., cust-12345"
            value={formData.customerId || ''}
            onChange={(e) => handleChange('customerId', e.target.value)}
          />
        </div>

        {/* Agent Name */}
        <div className="space-y-2">
          <Label htmlFor="agentName">Agent Name (Optional)</Label>
          <Input
            id="agentName"
            type="text"
            placeholder="e.g., John Doe"
            value={formData.agentName || ''}
            onChange={(e) => handleChange('agentName', e.target.value)}
          />
        </div>

        {/* Customer Name */}
        <div className="space-y-2">
          <Label htmlFor="customerName">Customer Name (Optional)</Label>
          <Input
            id="customerName"
            type="text"
            placeholder="e.g., Jane Smith"
            value={formData.customerName || ''}
            onChange={(e) => handleChange('customerName', e.target.value)}
          />
        </div>

        {/* Department */}
        <div className="space-y-2">
          <Label htmlFor="department">Department (Optional)</Label>
          <Input
            id="department"
            type="text"
            placeholder="e.g., Support"
            value={formData.department || ''}
            onChange={(e) => handleChange('department', e.target.value)}
          />
        </div>
      </div>
    </Card>
  );
}
