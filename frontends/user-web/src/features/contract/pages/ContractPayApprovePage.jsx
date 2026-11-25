import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import Button from '../components/Button';
import StatusMessage from '../components/StatusMessage';
import StepNav from '../components/StepNav';
import { approveKakaoPay } from '../lib/api';

// 카카오페이 approval_url 화면: /contract/pay/approve?applicationNo=..&orderId=..&pg_token=..
// 여기서 서버에 승인을 요청해야 실제 결제가 끝난다
export default function ContractPayApprovePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const applicationNo = params.get('applicationNo');
  const orderId = params.get('orderId');
  const pgToken = params.get('pg_token');

  const [error, setError] = useState(null);
  const [retryable, setRetryable] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const startedAttempt = useRef(-1); // StrictMode에서 effect가 두 번 돌아도 한 번만 요청

  useEffect(() => {
    if (startedAttempt.current === attempt) return;
    startedAttempt.current = attempt;

    if (!applicationNo || !orderId || !pgToken) {
      setError('결제 정보가 올바르지 않아요.');
      return;
    }
    setError(null);
    approveKakaoPay(applicationNo, { orderId, pgToken })
      .then(() => navigate(`/contract/${applicationNo}/complete`, { replace: true }))
      .catch((e) => {
        // 0·5xx: 결제 결과를 아직 모름 → 결제 화면으로 보내지 말고 같은 주문으로 다시 확인
        setRetryable(e.status === 0 || e.status >= 500);
        setError(e.message);
      });
  }, [applicationNo, orderId, pgToken, attempt, navigate]);

  if (!error) {
    return (
      <>
        <StepNav current={3} />
        <StatusMessage title="결제를 확인하는 중이에요" description="이 화면을 닫지 말고 잠시만 기다려주세요." />
      </>
    );
  }

  return (
    <>
      <StepNav current={3} />
      <StatusMessage
        title={retryable ? '결제 결과를 확인하지 못했어요' : '결제를 완료하지 못했어요'}
        description={error}
        action={
          retryable ? (
            <Button onClick={() => setAttempt((n) => n + 1)}>다시 확인</Button>
          ) : (
            applicationNo && (
              <Button variant="outline" onClick={() => navigate(`/contract/${applicationNo}/pay`, { replace: true })}>
                결제 화면으로
              </Button>
            )
          )
        }
      />
    </>
  );
}
